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
    abstract fun rangeReference(name: String): String

    fun getIndex(name: String, charCode: String): String =
        "binarySearchRange(${rangeReference(name)}, $charCode)"

    open fun getAt(name: String, index: String): String =
        "${rangeReference(name)}[$index]"

    abstract fun writeRange(name: String, elements: List<Int>, writer: FileWriter)
    abstract fun writeRanges(ranges: List<CategorizedRangePattern>, writer: FileWriter)

    open fun categoryValueFrom(): String = """
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

    abstract fun getCategoryValue(): String

    companion object {
        fun of(target: KotlinTarget, wrapperName: String, useBase64: Boolean = false): RangesWritingStrategy {
            return when (target) {
                KotlinTarget.JS, KotlinTarget.JS_IR -> if (useBase64) Base64JSRangesWritingStrategy(wrapperName) else JsRangesWritingStrategy(wrapperName)
                else -> NativeRangesWritingStrategy
            }
        }
    }
}

internal object NativeRangesWritingStrategy : RangesWritingStrategy() {
    override val indentation: String get() = ""
    override val rangesVisibilityModifier: String get() = "private"
    override fun beforeWritingRanges(writer: FileWriter) {}
    override fun afterWritingRanges(writer: FileWriter) {}
    override fun rangeReference(name: String): String = name

    override fun writeRange(name: String, elements: List<Int>, writer: FileWriter) =
        writer.writeIntArray(name, elements, this)

    override fun writeRanges(ranges: List<CategorizedRangePattern>, writer: FileWriter) {
        writer.writeIntArray("rangeStart", ranges.map { it.rangeStart() }, this)
        writer.appendLine()
        writer.writeIntArray("rangeEnd", ranges.map { it.rangeEnd() }, this)
        writer.appendLine()
        writer.writeIntArray("categoryOfRange", ranges.map { it.category() }, this)
    }

    override fun getCategoryValue(): String = """
        /**
         * Returns the Unicode general category of this character as an Int.
         */
        internal fun Char.getCategoryValue(): Int {
            val ch = this.toInt()
            val index = ${getIndex("rangeStart", "ch")}
            val high = ${getAt("rangeEnd", "index")}
            var value = CharCategory.UNASSIGNED.value
            if (ch <= high) {
                val code = ${getAt("categoryOfRange", "index")}
                value = categoryValueAt(index, ch)
            }
            return if (value == $UNASSIGNED_CATEGORY_VALUE_REPLACEMENT) CharCategory.UNASSIGNED.value else value
        }
        """.trimIndent()
}

// see KT-42461, KT-40482
internal open class JsRangesWritingStrategy(
    private val wrapperName: String
) : RangesWritingStrategy() {
    override val indentation: String get() = "        " // 8 spaces
    override val rangesVisibilityModifier: String get() = "internal"

    override fun beforeWritingRanges(writer: FileWriter) {
        writer.appendLine("private class $wrapperName {")
        writer.appendLine("    companion object {")
    }

    override fun afterWritingRanges(writer: FileWriter) {
        writer.appendLine("    }")
        writer.appendLine("}")
    }

    override fun rangeReference(name: String): String = "$wrapperName.$name"

    override fun writeRange(name: String, elements: List<Int>, writer: FileWriter) =
        writer.writeIntArray(name, elements, this)

    protected open fun writeShortRangeLength(name: String, elements: List<Int>, writer: FileWriter) =
        writer.writeIntArray(name, elements, this)

    protected open fun shortRangeGetAt(name: String, index: String): String =
        getAt(name, index)

    override fun writeRanges(ranges: List<CategorizedRangePattern>, writer: FileWriter) {
        val shortRanges = ranges.filter { it.rangeLength() < 64 }
        val longRanges = ranges.filter { it.rangeLength() >= 64 }

        this.writeRange("rangeStart", shortRanges.map { it.rangeStart() }, writer)
        writer.appendLine()
        this.writeShortRangeLength("rangeLength", shortRanges.map { it.rangeLength() }, writer)
        writer.appendLine()
        this.writeRange("categoryOfRange", shortRanges.map { it.category() }, writer)
        writer.appendLine()
        this.writeRange("longRangeStart", longRanges.map { it.rangeStart() }, writer)
        writer.appendLine()
        this.writeRange("longRangeLength", longRanges.map { it.rangeLength() }, writer)
        writer.appendLine()
        this.writeRange("categoryOfLongRange", longRanges.map { it.category() }, writer)
    }

    override fun getCategoryValue(): String = """
        /**
         * Returns the Unicode general category of this character as an Int.
         */
        internal fun Char.getCategoryValue(): Int {
            val ch = this.toInt()
            var value = CharCategory.UNASSIGNED.value
            
            var index = ${getIndex("rangeStart", "ch")}
            var start = ${getAt("rangeStart", "index")}
            var length = ${shortRangeGetAt("rangeLength", "index")}
            
            if (ch < start + length) {
                val code = ${getAt("categoryOfRange", "index")}
                value = categoryValueFrom(code, ch)
            } else {
                index = ${getIndex("longRangeStart", "ch")}
                start = ${getAt("longRangeStart", "index")}
                length = ${getAt("longRangeLength", "index")}
                
                if (ch < start + length) {
                    val code = ${getAt("categoryOfLongRange", "index")}
                    value = categoryValueFrom(code, ch)
                }
            }
            
            return if (value == $UNASSIGNED_CATEGORY_VALUE_REPLACEMENT) CharCategory.UNASSIGNED.value else value
        }
        """.trimIndent()
}

internal const val TO_BASE64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

internal class Base64JSRangesWritingStrategy(wrapperName: String) : JsRangesWritingStrategy(wrapperName) {

    override fun getAt(name: String, index: String): String =
        "intFromBase64(${rangeReference(name)}, $index)"

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

    override fun writeRange(name: String, elements: List<Int>, writer: FileWriter) =
        writer.writeIntsInBase64(name, elements, this)

    override fun writeShortRangeLength(name: String, elements: List<Int>, writer: FileWriter) =
        writer.write6BitsInBase64(name, elements, this)

    override fun shortRangeGetAt(name: String, index: String): String {
        return "${rangeReference("fromBase64")}[${rangeReference(name)}[$index].toInt()]"
    }

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
            val fromBase64 = CategoryRangesWrapper.fromBase64
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