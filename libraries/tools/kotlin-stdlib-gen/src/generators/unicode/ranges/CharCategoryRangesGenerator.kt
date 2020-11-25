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

internal class CharCategoryRangesGenerator(
    private val outputFile: File,
    target: KotlinTarget
) : UnicodeDataGenerator {
    private val ranges = mutableListOf<CategorizedRangePattern>()
    private val writingStrategy = RangesWritingStrategy.of(target, "Category", encoding = RangeEncoding.VARIABLE_LENGTH_BASE64)

    init {
        outputFile.parentFile.requireExistingDir()
    }

    override fun appendChar(char: String, name: String, categoryCode: String) {
        val charCode = char.toInt(radix = 16)

        require(charCode == 0 || ranges.isNotEmpty())

        if (ranges.isEmpty()) {
            ranges.add(CategorizedConsequentPattern(charCode, categoryCode))
            return
        }

        val lastRange = ranges.last()

        if (name.endsWith(", Last>")) {
            check(lastRange is CategorizedConsequentPattern)
            check(lastRange.categoryCode == categoryCode)
            lastRange.setEnd(charCode)
            return
        }

        for (unassignedCharCode in lastRange.rangeEnd() + 1 until charCode) {
            appendChar(unassignedCharCode, CharCategory.UNASSIGNED.code)
        }

        if (name.endsWith(", First>")) {
            ranges.add(CategorizedConsequentPattern(charCode, categoryCode))
            return
        }

        appendChar(charCode, categoryCode)
    }

    override fun close() {
        for (unassignedCharCode in ranges.last().rangeEnd() + 1..0xffff) {
            appendChar(unassignedCharCode, CharCategory.UNASSIGNED.code)
        }

        FileWriter(outputFile).use { writer ->
            writer.writeHeader(outputFile, "kotlin.text")
            writer.appendLine()
            writer.appendLine("// ${ranges.size} ranges totally")
            writer.writeRanges()
            writer.appendLine()
            writer.appendLine(writingStrategy.categoryValueFrom())
            writer.appendLine()
            writer.appendLine(writingStrategy.getCategoryValue())
        }
    }

    private fun appendChar(charCode: Int, categoryCode: String) {
        val newLastRange = ranges.last().append(charCode, categoryCode)
        if (newLastRange != null) {
            ranges[ranges.lastIndex] = newLastRange
        } else {
            ranges.add(CategorizedConsequentPattern(charCode, categoryCode))
        }
    }

    private fun FileWriter.writeRanges() {
        writingStrategy.beforeWritingRanges(this)
        writingStrategy.writeRanges(ranges, this)
        writingStrategy.afterWritingRanges(this)
    }
}
