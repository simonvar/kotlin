/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.ranges

import generators.requireExistingDir
import generators.unicode.UnicodeDataGenerator
import generators.unicode.ranges.writers.DigitRangesWriter
import generators.unicode.ranges.writers.writeHeader
import templates.KotlinTarget
import java.io.File
import java.io.FileWriter

internal class DigitRangesGenerator(
    private val outputFile: File,
    target: KotlinTarget
) : UnicodeDataGenerator {
    private val start = mutableListOf<Int>()
    private val end = mutableListOf<Int>()
    private val rangesWriter = DigitRangesWriter(RangesWritingStrategy.of(target, "Digit"))

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
            writer.appendLine("// ${start.size} ranges totally")
            rangesWriter.write(start, writer)
        }
    }
}