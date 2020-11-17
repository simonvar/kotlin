/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode

import generators.requireExistingDir
import java.io.File
import java.io.FileWriter

internal class WhitespaceRangesGenerator(
    private val outputFile: File
) : UnicodeDataGenerator {
    // Cc CONTROL spaces
    private val start = mutableListOf(0x9, 0x1C)
    private val end = mutableListOf(0xD, 0x1F)

    private val whitespaceCategories = listOf(
        CharCategory.SPACE_SEPARATOR.code,
        CharCategory.LINE_SEPARATOR.code,
        CharCategory.PARAGRAPH_SEPARATOR.code
    )

    init {
        outputFile.parentFile.requireExistingDir()
    }

    override fun appendChar(char: String, name: String, categoryCode: String) {
        if (categoryCode !in whitespaceCategories) {
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
        FileWriter(outputFile).use { writer ->
            writer.writeHeader(outputFile, "kotlin.text")
            writer.appendLine()
            writer.appendLine(isWhitespaceImpl())
        }
    }

    private fun isWhitespaceImpl(): String {
        val checkSeparator = "\n${"    ".repeat(5)}|| "
        return """
        /**
         * Returns `true` if this character is a whitespace.
         */
        internal fun Char.isWhitespaceImpl(): Boolean {
            val ch = this.toInt()
            return ${rangeChecks("ch").joinToString(checkSeparator)}
        }
        """.trimIndent()
    }

    private fun rangeChecks(ch: String): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until start.size) {
            val rangeStart = start[i]
            val rangeEnd = end[i]
            when (rangeStart) {
                rangeEnd -> {
                    result.add("$ch == ${rangeStart.hex()}")
                }
                rangeEnd - 1 -> {
                    result.add("$ch == ${rangeStart.hex()}")
                    result.add("$ch == ${rangeEnd.hex()}")
                }
                else -> {
                    result.add("$ch in ${rangeStart.hex()}..${rangeEnd.hex()}")
                }
            }
        }
        return result
    }
}
