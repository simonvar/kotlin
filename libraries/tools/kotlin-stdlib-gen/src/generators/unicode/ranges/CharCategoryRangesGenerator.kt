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
    private val writingStrategy = RangesWritingStrategy.of(target, "Category", useBase64 = false)

    init {
        outputFile.parentFile.requireExistingDir()
    }

    override fun appendChar(char: String, name: String, categoryCode: String) {
        val charCode = char.toInt(radix = 16)

        require(charCode == 0 || ranges.isNotEmpty())

        if (ranges.isEmpty() || name.endsWith(", First>")) {
            ranges.add(CategorizedConsequentPattern(charCode, categoryCode))
            return
        }

        var lastRange = ranges.last()

        if (name.endsWith(", Last>")) {
            check(lastRange is CategorizedConsequentPattern)
            check(lastRange.categoryCode == categoryCode)
            lastRange.setEnd(charCode)
            return
        }

        fun append(charCode: Int, categoryCode: String) {
            val newLastRange = lastRange.append(charCode, categoryCode)
            if (newLastRange != null) {
                lastRange = newLastRange
                ranges[ranges.lastIndex] = lastRange
            } else {
//                println(lastRange)
                lastRange = CategorizedConsequentPattern(charCode, categoryCode)
                ranges.add(lastRange)
            }
        }

        for (unassignedCharCode in lastRange.rangeEnd() + 1 until charCode) {
            append(unassignedCharCode, CharCategory.UNASSIGNED.code)
        }
        append(charCode, categoryCode)
    }

    override fun close() {
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

    private fun FileWriter.writeRanges() {
        writingStrategy.beforeWritingRanges(this)
        writingStrategy.writeRanges(ranges, this)
        writingStrategy.afterWritingRanges(this)
    }
}
