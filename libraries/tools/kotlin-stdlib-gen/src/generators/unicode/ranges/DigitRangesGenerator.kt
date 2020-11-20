/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.ranges

import generators.requireExistingDir
import generators.unicode.*
import templates.KotlinTarget
import java.io.File
import java.io.FileWriter

internal class DigitRangesGenerator(
    private val outputFile: File,
    target: KotlinTarget
) : UnicodeDataGenerator {
    private val start = mutableListOf<Int>()
    private val end = mutableListOf<Int>()
    private val writingStrategy = RangesWritingStrategy.of(target, "DigitRangesWrapper")

    init {
        outputFile.parentFile.requireExistingDir()
    }

    override fun appendChar(char: String, name: String, categoryCode: String) {
        if (categoryCode != CharCategory.DECIMAL_DIGIT_NUMBER.code) {
            return
        }

        val charCode = char.toInt(radix = 16)

        check(!name.endsWith(", First>") && !name.endsWith(", Last>"))

        if (end.lastOrNull() == charCode - 1) {
            end[end.lastIndex] = charCode
        } else {
            start.add(charCode)
            end.add(charCode)
        }
    }

    override fun close() {
        // digit ranges always have length equal to 10, so that the difference between the last char code in range and the first one is always 9.
        // Therefore, no need to generate ranges end
        for (i in 0 until start.size) {
            check(end[i] - start[i] == 9)
        }

        FileWriter(outputFile).use { writer ->
            writer.writeHeader(outputFile, "kotlin.text")
            writer.appendLine()
            writer.writeRanges()
            writer.appendLine()
            writer.appendLine(binarySearch())
            writer.appendLine()
            writer.appendLine(isDigitImpl())
        }
    }

    private fun FileWriter.writeRanges() {
        writingStrategy.beforeWritingRanges(this)
        writeIntArray("rangeStart", start, writingStrategy)
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

    private fun isDigitImpl(): String {
        val rangeStart = writingStrategy.rangeReference("rangeStart")
        return """
        /**
         * Returns `true` if this character is a digit.
         */
        internal fun Char.isDigitImpl(): Boolean {
            val ch = this.toInt()
            val index = binarySearchRange($rangeStart, ch)
            val high = $rangeStart[index] + 9
            return ch <= high
        }
        """.trimIndent()
    }
}