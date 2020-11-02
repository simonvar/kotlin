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
    private val writingStrategy = RangesWritingStrategy.of(target, "CategoryRangesWrapper")

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
            writer.appendLine(binarySearch())
            writer.appendLine()
            writer.appendLine(getCategoryValue())
        }
    }

    private fun FileWriter.writeRanges() {
        writingStrategy.beforeWritingRanges(this)
        writeIntArray("rangeStart", start, writingStrategy) { it }
        appendLine()
        writeIntArray("rangeEnd", end, writingStrategy) { it }
        appendLine()

        val categoryCodeToValue = CharCategory.values().associateBy({ it.code }, { it.value })

        writeIntArray("categoryOfRange", category, writingStrategy) { (even, odd) ->
            if (even == null || odd == null || even == odd) {
                categoryCodeToValue[even ?: odd]!!
            } else {
                categoryCodeToValue[even]!! + (categoryCodeToValue[odd]!! shl 8)
            }
        }
        writingStrategy.afterWritingRanges(this)
    }

    private fun binarySearch(): String = """
        internal fun binarySearchRange(array: IntArray, needle: Int): Int {
            var bottom = 0
            var top = array.size - 1
            var middle = -1
            var value = 0
            while (bottom <= top) {
                middle = (bottom + top) / 2
                value = array[middle]
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

    private fun getCategoryValue(): String = """
        /**
         * Returns the Unicode general category of this character as an Int.
         */
        internal fun Char.getCategoryValue(): Int {
            val ch = this.toInt()
            val index = binarySearchRange(${writingStrategy.rangeReference("rangeStart")}, ch)
            val high = ${writingStrategy.rangeReference("rangeEnd[index]")}
            if (ch <= high) {
                val code = ${writingStrategy.rangeReference("categoryOfRange")}[index]
                if (code < 0x100) {
                    return code
                }
                return if ((ch and 1) == 1) code shr 8 else code and 0xff
            }
            return CharCategory.UNASSIGNED.value
        }
        """.trimIndent()
}
