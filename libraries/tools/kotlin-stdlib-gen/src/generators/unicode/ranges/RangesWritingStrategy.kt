/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.ranges

import generators.unicode.*
import templates.KotlinTarget
import java.io.FileWriter

internal sealed class RangesWritingStrategy {
    abstract val indentation: String
    abstract val rangesVisibilityModifier: String
    abstract fun beforeWritingRanges(writer: FileWriter)
    abstract fun afterWritingRanges(writer: FileWriter)
    abstract fun rangeRef(name: String): String

    fun getIndex(name: String, charCode: String): String =
        "binarySearchRange(${rangeRef(name)}, $charCode)"

    open fun getAt(name: String, index: String): String =
        "${rangeRef(name)}[$index]"

    fun writeRanges(ranges: List<CategorizedRangePattern>, writer: FileWriter) {
        this.writeRangeStart("rangeStart", ranges.map { it.rangeStart() }, writer)
        writer.appendLine()
        this.writeRangeCategory("rangeCategory", ranges.map { it.category() }, writer)
    }

    abstract fun writeRangeStart(name: String, elements: List<Int>, writer: FileWriter)
    abstract fun writeRangeCategory(name: String, elements: List<Int>, writer: FileWriter)

    fun categoryValueFrom(): String = """
        private fun categoryValueFrom(code: Int, ch: Int): Int {
            return when {
                code < 0x20 -> code
                code < 0x400 -> if ((ch and 1) == 1) code shr 5 else code and 0x1f
                else ->
                    when (ch % 3) {
                        2 -> code shr 10
                        1 -> (code shr 5) and 0x1f
                        else -> code and 0x1f
                    }
            }
        }
        """.trimIndent()

    open fun getCategoryValue(): String = """
        /**
         * Returns the Unicode general category of this character as an Int.
         */
        internal fun Char.getCategoryValue(): Int {
            val ch = this.toInt()

            val index = ${getIndex("rangeStart", "ch")}
            val code = ${getAt("rangeCategory", "index")}
            val value = categoryValueFrom(code, ch)

            return if (value == $UNASSIGNED_CATEGORY_VALUE_REPLACEMENT) CharCategory.UNASSIGNED.value else value
        }
        """.trimIndent()

    companion object {
        fun of(target: KotlinTarget, wrapperName: String, encoding: RangeEncoding = RangeEncoding.INT_LITERAL): RangesWritingStrategy {
            return when (target) {
                KotlinTarget.JS, KotlinTarget.JS_IR -> when (encoding) {
                    RangeEncoding.INT_LITERAL -> JsRangesWritingStrategy(wrapperName)
                    RangeEncoding.FIXED_LENGTH_BASE64 -> Base64JSRangesWritingStrategy(wrapperName)
                    RangeEncoding.VARIABLE_LENGTH_BASE64 -> VariableLengthBase64Strategy(wrapperName)
                }
                else -> NativeRangesWritingStrategy
            }
        }
    }
}

internal enum class RangeEncoding {
    INT_LITERAL, FIXED_LENGTH_BASE64, VARIABLE_LENGTH_BASE64
}

internal object NativeRangesWritingStrategy : RangesWritingStrategy() {
    override val indentation: String get() = ""
    override val rangesVisibilityModifier: String get() = "private"
    override fun beforeWritingRanges(writer: FileWriter) {}
    override fun afterWritingRanges(writer: FileWriter) {}
    override fun rangeRef(name: String): String = name

    override fun writeRangeStart(name: String, elements: List<Int>, writer: FileWriter) {
        writer.writeIntArray(name, elements, this)
    }

    override fun writeRangeCategory(name: String, elements: List<Int>, writer: FileWriter) {
        writer.writeIntArray(name, elements, this)
    }
}

// see KT-42461, KT-40482
internal open class JsRangesWritingStrategy(
    protected val wrapperName: String
) : RangesWritingStrategy() {
    override val indentation: String get() = " ".repeat(4)
    override val rangesVisibilityModifier: String get() = "internal"

    override fun beforeWritingRanges(writer: FileWriter) {
        writer.appendLine("private object $wrapperName {")
    }

    override fun afterWritingRanges(writer: FileWriter) {
        writer.appendLine("}")
    }

    override fun rangeRef(name: String): String = "$wrapperName.$name"

    override fun writeRangeStart(name: String, elements: List<Int>, writer: FileWriter) =
        writer.writeIntArray(name, elements, this)

    override fun writeRangeCategory(name: String, elements: List<Int>, writer: FileWriter) =
        writer.writeIntArray(name, elements, this)
}

internal const val TO_BASE64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

internal class Base64JSRangesWritingStrategy(wrapperName: String) : JsRangesWritingStrategy(wrapperName) {

    override fun getAt(name: String, index: String): String =
        "intFromBase64(${rangeRef(name)}, $index)"

    override fun beforeWritingRanges(writer: FileWriter) {
        super.beforeWritingRanges(writer)
        writer.appendLine("${indentation}private const val toBase64 = \"$TO_BASE64\"")
        writer.appendLine("${indentation}internal val fromBase64 = IntArray(128).apply { toBase64.forEachIndexed { index, char -> this[char.toInt()] = index } }")
        writer.appendLine()
    }

    override fun afterWritingRanges(writer: FileWriter) {
        super.afterWritingRanges(writer)
        writer.appendLine()
        writer.appendLine(binarySearch())
        writer.appendLine()
        writer.appendLine(intFromBase64())
    }

    override fun writeRangeStart(name: String, elements: List<Int>, writer: FileWriter) =
        writer.writeInBase64(name, elements, this)

    override fun writeRangeCategory(name: String, elements: List<Int>, writer: FileWriter) =
        writer.writeInBase64(name, elements, this)

    private fun binarySearch(): String = """
        private fun binarySearchRange(rangeStart: String, needle: Int): Int {
            var bottom = 0
            val padding = if (rangeStart[rangeStart.length - 2] == '=') 2 else if (rangeStart[rangeStart.length - 1] == '=') 1 else 0
            var top = (((rangeStart.length - padding) * 6) - 2 * padding) / 16 - 1
            var middle = -1
            var value = 0
            while (bottom <= top) {
                middle = (bottom + top) / 2
                value = intFromBase64(rangeStart, middle)
                if (needle > value)
                    bottom = middle + 1
                else if (needle == value)
                    return middle
                else
                    top = middle - 1
            }
            return middle - (if (needle < value) 1 else 0)
        }
        """.trimIndent()

    private fun intFromBase64(): String = """
        private fun intFromBase64(string: String, index: Int): Int {
            val fromBase64 = $wrapperName.fromBase64
            val stringIndex = (index / 3) * 8
            return when (index % 3) {
                0 -> (fromBase64[string[stringIndex].toInt()] shl 10) or
                        (fromBase64[string[stringIndex + 1].toInt()] shl 4) or
                        (fromBase64[string[stringIndex + 2].toInt()] shr 2)
        
                1 -> ((fromBase64[string[stringIndex + 2].toInt()] and 0b11) shl 14) or
                        (fromBase64[string[stringIndex + 3].toInt()] shl 8) or
                        (fromBase64[string[stringIndex + 4].toInt()] shl 2) or
                        (fromBase64[string[stringIndex + 5].toInt()] shr 4)
        
                2 -> ((fromBase64[string[stringIndex + 5].toInt()] and 0b1111) shl 12) or
                        (fromBase64[string[stringIndex + 6].toInt()] shl 6) or
                        (fromBase64[string[stringIndex + 7].toInt()])
        
                else -> error("Unreachable")
            }
        }
        """.trimIndent()
}

internal class VariableLengthBase64Strategy(wrapperName: String) : JsRangesWritingStrategy(wrapperName) {

    override fun beforeWritingRanges(writer: FileWriter) {
        super.beforeWritingRanges(writer)
        writer.appendLine("${indentation}private const val toBase64 = \"$TO_BASE64\"")
        writer.appendLine("${indentation}internal val fromBase64 = IntArray(128).apply { toBase64.forEachIndexed { index, char -> this[char.toInt()] = index } }")
        writer.appendLine()
    }

    override fun afterWritingRanges(writer: FileWriter) {
        writer.appendLine()
        writer.appendLine("${indentation}internal var decodedRangeStart = intArrayOf()")
        writer.appendLine("${indentation}internal var decodedRangeCategory = intArrayOf()")
        super.afterWritingRanges(writer)
        writer.appendLine()
        writer.appendLine(decodeVarLenBase64())
    }

    override fun writeRangeStart(name: String, elements: List<Int>, writer: FileWriter) {
        val diff = elements.zipWithNext { a, b -> b - a }
        writer.writeInVariableLengthBase64(name, diff, this)
    }

    override fun writeRangeCategory(name: String, elements: List<Int>, writer: FileWriter) =
        writer.writeInVariableLengthBase64(name, elements, this)

    private fun decodeVarLenBase64() = """
        private fun decodeVarLenBase64(base64: String): MutableList<Int> {
            val result = mutableListOf<Int>()
            var int = 0
            var shift = 0
            for (char in base64) {
                val sixBit = Category.fromBase64[char.toInt()]
                int = int or ((sixBit and 0x1f) shl shift)
                if (sixBit < 0x20) {
                    result.add(int)
                    int = 0
                    shift = 0
                } else {
                    shift += 5
                }
            }
            return result
        }
        """.trimIndent()

    override fun getCategoryValue(): String = """
        /**
         * Returns the Unicode general category of this character as an Int.
         */
        internal fun Char.getCategoryValue(): Int {
            if (${rangeRef("decodedRangeStart")}.isEmpty()) {
                val diff = decodeVarLenBase64(${rangeRef("rangeStart")})
                val values = IntArray(diff.size + 1)
                for (i in 0 until diff.size) {
                    values[i + 1] = values[i] + diff[i]
                }
        
                ${rangeRef("decodedRangeStart")} = values
                ${rangeRef("decodedRangeCategory")} = decodeVarLenBase64(Category.rangeCategory).toIntArray()
            }
        
            val ch = this.toInt()
        
            val index = ${getIndex("decodedRangeStart", "ch")}
            val code = ${getAt("decodedRangeCategory", "index")}
            val value = categoryValueFrom(code, ch)
        
            return if (value == 17) CharCategory.UNASSIGNED.value else value
        }
        """.trimIndent()
}