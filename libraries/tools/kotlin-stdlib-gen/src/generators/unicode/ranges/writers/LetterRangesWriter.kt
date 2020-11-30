/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.ranges.writers

import generators.unicode.ranges.RangesWritingStrategy
import generators.unicode.ranges.patterns.RangePattern
import java.io.FileWriter

internal open class LetterRangesWriter(protected val strategy: RangesWritingStrategy) {
    fun write(ranges: List<RangePattern>, writer: FileWriter) {
        beforeWritingRanges(writer)

        writeRangeStart(ranges.map { it.rangeStart() }, writer)
        writer.appendLine()
        writeRangeEnd(ranges.map { it.rangeEnd() }, writer)
        writeInit(ranges.size, writer)

        afterWritingRanges(writer)
    }

    protected open fun beforeWritingRanges(writer: FileWriter) {
        strategy.beforeWritingRanges(writer)
    }

    protected open fun afterWritingRanges(writer: FileWriter) {
        strategy.afterWritingRanges(writer)
        writer.appendLine()
        writer.appendLine(isLetterImpl())
    }

    protected open fun writeRangeStart(elements: List<Int>, writer: FileWriter) {
        writer.writeIntArray("rangeStart", elements, strategy)
    }

    protected open fun writeRangeEnd(elements: List<Int>, writer: FileWriter) {
        writer.writeIntArray("rangeEnd", elements, strategy)
    }

    protected open fun writeInit(rangeCount: Int, writer: FileWriter) {}

    private fun isLetterImpl(): String = """
        /**
         * Returns `true` if this character is a letter.
         */
        internal fun Char.isLetterImpl(): Boolean {
            val ch = this.toInt()
            val index = ${indexOf("ch")}

            val rangeStart = ${startAt("index")}
            val rangeEnd = ${endAt("index")}

            if (rangeEnd <= 0xffff) {
                return ch <= rangeEnd
            }

            val isGapPattern = rangeEnd <= 0x3fff_ffff
            if (isGapPattern) {
                if (ch > rangeEnd and 0xffff) {
                    return false
                }
                val charsBeforeGap = rangeEnd shr 24
                val gapLength = (rangeEnd shr 16) and 0xff
                val chDistance = ch - rangeStart
                return chDistance < charsBeforeGap || chDistance >= charsBeforeGap + gapLength
            }

            // isBitPattern
            if (ch > rangeStart + 30) {
                return false
            }
            val shift = ch - rangeStart - 1
            return (ch == rangeStart) || rangeEnd and (1 shl shift) > 0
        }
        """.trimIndent()

    protected open fun indexOf(charCode: String): String {
        return "binarySearchRange(${strategy.rangeRef("rangeStart")}, $charCode)"
    }

    protected open fun startAt(index: String): String {
        return "${strategy.rangeRef("rangeStart")}[$index]"
    }

    protected open fun endAt(index: String): String {
        return "${strategy.rangeRef("rangeEnd")}[$index]"
    }
}

internal class VarLenBase64LetterRangesWriter(strategy: RangesWritingStrategy) : LetterRangesWriter(strategy) {

    override fun writeRangeStart(elements: List<Int>, writer: FileWriter) {
        writer.writeInVarLenBase64("rangeStart", elements, strategy);
    }

    override fun writeRangeEnd(elements: List<Int>, writer: FileWriter) {
        writer.writeInVarLenBase64("rangeEnd", elements, strategy);
    }

    override fun writeInit(rangeCount: Int, writer: FileWriter) {
        writer.appendLine(
            """
            
            internal val decodedRangeStart: IntArray
            internal val decodedRangeEnd: IntArray
            
            init {
                val toBase64 = "$TO_BASE64"
                val fromBase64 = IntArray(128)
                for (i in toBase64.indices) {
                    fromBase64[toBase64[i].toInt()] = i
                }
                decodedRangeStart = decodeVarLenBase64(rangeStart, fromBase64, $rangeCount)
                decodedRangeEnd = decodeVarLenBase64(rangeEnd, fromBase64, $rangeCount)
            }
            """.replaceIndent(strategy.indentation)
        )
    }

    override fun indexOf(charCode: String): String {
        return "binarySearchRange(${strategy.rangeRef("decodedRangeStart")}, $charCode)"
    }

    override fun startAt(index: String): String {
        return "${strategy.rangeRef("decodedRangeStart")}[$index]"
    }

    override fun endAt(index: String): String {
        return "${strategy.rangeRef("decodedRangeEnd")}[$index]"
    }
}
