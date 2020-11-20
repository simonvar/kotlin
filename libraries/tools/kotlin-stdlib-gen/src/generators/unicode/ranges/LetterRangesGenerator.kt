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

internal class LetterRangesGenerator(
    private val outputFile: File,
    target: KotlinTarget
) : UnicodeDataGenerator {
    private val ranges = mutableListOf<RangePattern>()
    private val writingStrategy = RangesWritingStrategy.of(target, "LetterRangesWrapper")

    private val letterCategoryCodes = listOf(
        CharCategory.UPPERCASE_LETTER.code,
        CharCategory.LOWERCASE_LETTER.code,
        CharCategory.TITLECASE_LETTER.code,
        CharCategory.MODIFIER_LETTER.code,
        CharCategory.OTHER_LETTER.code
    )

    init {
        outputFile.parentFile.requireExistingDir()
    }

    override fun appendChar(char: String, name: String, categoryCode: String) {
        if (categoryCode !in letterCategoryCodes) {
            return
        }

        val charCode = char.toInt(radix = 16)
//        println("$char $categoryCode $name")

        if (ranges.isEmpty() || name.endsWith(", First>")) {
            ranges.add(ConsequentPattern(charCode))
            return
        }

        val lastRange = ranges.last()

        if (name.endsWith(", Last>")) {
            check(lastRange is ConsequentPattern)
            lastRange.setEnd(charCode)
            return
        }

        val newLastRange = lastRange.append(charCode)
        if (newLastRange != null) {
            ranges[ranges.lastIndex] = newLastRange
        } else {
//            println(lastRange)
            ranges.add(ConsequentPattern(charCode))
        }
    }

    override fun close() {
        FileWriter(outputFile).use { writer ->
            writer.writeHeader(outputFile, "kotlin.text")
            writer.appendLine()
            writer.writeRanges()
            writer.appendLine()
            writer.appendLine(isLetterImpl())
        }
    }

    private fun FileWriter.writeRanges() {
        writingStrategy.beforeWritingRanges(this)
        writeIntArray("rangeStart", ranges.map { it.rangeStart() }, writingStrategy)
        appendLine()
        writeIntArray("rangeEnd", ranges.map { it.rangeEnd() }, writingStrategy)
        writingStrategy.afterWritingRanges(this)
    }

    private fun isLetterImpl(): String {
        val rangeStart = writingStrategy.rangeReference("rangeStart")
        val rangeEnd = writingStrategy.rangeReference("rangeEnd")
        return """
        /**
         * Returns `true` if this character is a letter.
         */
        internal fun Char.isLetterImpl(): Boolean {
            val ch = this.toInt()
            val index = binarySearchRange($rangeStart, ch)

            val rangeStart = $rangeStart[index]
            val rangeEnd = $rangeEnd[index]

            val isGapPattern = rangeEnd > 0xffff
            if (isGapPattern) {
                if (ch > rangeEnd and 0xffff) {
                    return false
                }
                val charsBeforeGap = rangeEnd shr 24
                val gapLength = (rangeEnd shr 16) and 0xff
                val chDistance = ch - rangeStart
                return chDistance < charsBeforeGap || chDistance >= charsBeforeGap + gapLength
            }

            val isBitPattern = rangeEnd < 0
            if (isBitPattern) {
                if (ch > rangeStart + 31) {
                    return false
                }
                val shift = ch - rangeStart - 1
                return (ch == rangeStart) || rangeEnd and (1 shl shift) > 0
            }

            return ch <= rangeEnd
        }
        """.trimIndent()
    }
}
