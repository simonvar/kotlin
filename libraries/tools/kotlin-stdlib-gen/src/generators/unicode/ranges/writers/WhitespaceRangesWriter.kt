/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.ranges.writers

import java.io.FileWriter

internal class WhitespaceRangesWriter {
    fun write(rangeStart: List<Int>, rangeEnd: List<Int>, writer: FileWriter) {
        writer.appendLine(isWhitespaceImpl(rangeStart, rangeEnd))
    }

    private fun isWhitespaceImpl(rangeStart: List<Int>, rangeEnd: List<Int>): String {
        val checkSeparator = "\n${"    ".repeat(5)}|| "
        val checks = rangeChecks(rangeStart, rangeEnd, "ch").joinToString(checkSeparator)
        return """
        /**
         * Returns `true` if this character is a whitespace.
         */
        internal fun Char.isWhitespaceImpl(): Boolean {
            val ch = this.toInt()
            return $checks
        }
        """.trimIndent()
    }

    private fun rangeChecks(rangeStart: List<Int>, rangeEnd: List<Int>, ch: String): List<String> {
        val result = mutableListOf<String>()
        for (i in rangeStart.indices) {
            val start = rangeStart[i]
            val end = rangeEnd[i]
            when (start) {
                end -> {
                    result.add("$ch == ${start.hex()}")
                }
                end - 1 -> {
                    result.add("$ch == ${start.hex()}")
                    result.add("$ch == ${end.hex()}")
                }
                else -> {
                    result.add("$ch in ${start.hex()}..${end.hex()}")
                }
            }
        }
        return result
    }
}