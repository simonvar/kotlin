/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.ranges

import generators.requireExistingDir
import generators.unicode.UnicodeDataGenerator
import generators.unicode.ranges.patterns.ConsequentPattern
import generators.unicode.ranges.patterns.RangePattern
import generators.unicode.ranges.writers.LetterRangesWriter
import generators.unicode.ranges.writers.writeHeader
import templates.KotlinTarget
import java.io.File
import java.io.FileWriter

internal class LetterRangesGenerator(
    private val outputFile: File,
    target: KotlinTarget
) : UnicodeDataGenerator {
    private val ranges = mutableListOf<RangePattern>()
    private val rangesWriter = LetterRangesWriter(RangesWritingStrategy.of(target, "Letter"))

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
            writer.appendLine("// ${ranges.size} ranges totally")
            rangesWriter.write(ranges, writer)
        }
    }
}
