/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode

import generators.requireExistingDir
import templates.KotlinTarget
import java.io.File
import java.io.FileWriter

internal class CharCategoryRangesGenerator(
    private val outputFile: File,
    target: KotlinTarget
) : UnicodeDataGenerator {
    private val start = mutableListOf<Int>()
    private val end = mutableListOf<Int>()
    private val category = mutableListOf<MutableList<String?>>()
    private val writingStrategy = RangesWritingStrategy.of(target, "CategoryRangesWrapper", useBase64 = true)

    init {
        outputFile.parentFile.requireExistingDir()
    }

    override fun appendChar(char: String, name: String, categoryCode: String) {
        val charCode = char.toInt(radix = 16)

        require(charCode == 0 || start.isNotEmpty())

        if (start.isEmpty() || name.endsWith(", First>")) {
            start.add(charCode)
            end.add(charCode)
            category.add(mutableListOf(categoryCode, null))
            return
        }
        if (name.endsWith(", Last>")) {
            end[end.lastIndex] = charCode
            check(category.last().first() == categoryCode)
            return
        }

        val lastCategory = category.last()[charCode and 1]

        if (end.last() == charCode - 1 && (lastCategory == null || lastCategory == categoryCode)) {
            end[end.lastIndex] = charCode
            category.last()[charCode and 1] = categoryCode
        } else {
            start.add(charCode)
            end.add(charCode)
            category.add(mutableListOf(null, null))
            category.last()[charCode and 1] = categoryCode
        }
    }

    override fun close() {
        FileWriter(outputFile).use { writer ->
            writer.writeHeader(outputFile, "kotlin.text")
            writer.appendLine()
            writer.writeRanges()
            writer.appendLine()
            if (writingStrategy.useBase64) {
                writer.appendLine(binarySearch())
                writer.appendLine()
                writer.appendLine(intFromBase64())
                writer.appendLine()
            }
            writer.appendLine(getCategoryValue())
        }
    }

    private fun FileWriter.writeRanges() {
        writingStrategy.beforeWritingRanges(this)
        writingStrategy.writeRange("rangeStart", start, this)
        appendLine()
        writingStrategy.writeRange("rangeEnd", end, this)
        appendLine()

        val categoryCodeToValue = CharCategory.values().associateBy({ it.code }, { it.value })

        val intCategories = category.map { (even, odd) ->
            if (even == null || odd == null || even == odd) {
                categoryCodeToValue[even ?: odd]!!
            } else {
                categoryCodeToValue[even]!! + (categoryCodeToValue[odd]!! shl 8)
            }
        }

        writingStrategy.writeRange("categoryOfRange", intCategories, this)
        writingStrategy.afterWritingRanges(this)
    }

    private fun binarySearch(): String = """
        private fun binarySearchRange(rangeStart: String, needle: Int): Int {
            var bottom = 0
            var top = ${start.size} - 1
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

    private fun getCategoryValue(): String {
        fun getAt(name: String, index: String): String {
            return if (writingStrategy.useBase64)
                "intFromBase64(${writingStrategy.rangeReference(name)}, $index)"
            else
                "${writingStrategy.rangeReference(name)}[$index]"
        }
        return """
        /**
         * Returns the Unicode general category of this character as an Int.
         */
        internal fun Char.getCategoryValue(): Int {
            val ch = this.toInt()
            val index = binarySearchRange(${writingStrategy.rangeReference("rangeStart")}, ch)
            val high = ${getAt("rangeEnd", "index")}
            if (ch <= high) {
                val code = ${getAt("categoryOfRange", "index")}
                if (code < 0x100) {
                    return code
                }
                return if ((ch and 1) == 1) code shr 8 else code and 0xff
            }
            return CharCategory.UNASSIGNED.value
        }
        """.trimIndent()
    }
}
