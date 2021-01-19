/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.ranges.builders

import generators.unicode.ranges.patterns.CategorizedPeriodicPattern
import generators.unicode.ranges.patterns.CategorizedRangePattern
import generators.unicode.ranges.patterns.rangeLength

internal abstract class RangesBuilder {
    private val ranges = mutableListOf<CategorizedRangePattern>()

    open fun append(char: String, name: String, categoryCode: String) {
        val charCode = char.toInt(radix = 16)
        val categoryId = categoryId(categoryCode)

        if (name.endsWith(", First>")) {
            rangeFirst(charCode, categoryId)
            return
        }
        if (name.endsWith(", Last>")) {
            rangeLast(charCode, categoryId)
            return
        }
        append(charCode, categoryId)
    }

    fun build(): Triple<List<Int>, List<Int>, List<Int>> {
        for (code in ranges.last().rangeEnd() + 1..0xffff) {
            appendSingleChar(code, unassignedCategoryId)
        }

        var index = ranges.lastIndex
        while (index > 0) {
            val previous = ranges[index - 1]
            val previousEnd = previous.rangeEnd()
            val previousEndCategory = previous.categoryIdOf(previousEnd)
            val current = ranges[index]
            if (current.prepend(previousEnd, previousEndCategory)) {
                val newPrevious = removeLast(previous)
                if (newPrevious != null) {
                    ranges[index - 1] = newPrevious
                } else {
                    ranges.removeAt(index - 1)
                    index--
                }
            } else {
                index--
            }
        }

        if (this is LetterRangesBuilder) {
            println(ranges.joinToString(separator = "\n"))
        }

//        if (this is CharCategoryRangesBuilder) {
//            println(ranges.subList(fromIndex = 0, toIndex = 10).joinToString(separator = "\n"))
//        }

        return Triple(ranges.map { it.rangeStart() }, ranges.map { it.rangeEnd() }, ranges.map { it.category() })
    }

    private fun rangeFirst(charCode: Int, categoryId: String) {
        append(charCode, categoryId)
    }

    private fun rangeLast(charCode: Int, categoryId: String) {
        if (shouldSkip(categoryId)) return

        val lastChar = ranges.last().rangeEnd() // must be non-empty

        check(ranges.last().categoryIdOf(lastChar) == categoryId)

        for (code in lastChar + 1..charCode) {
            appendSingleChar(code, categoryId)
        }
    }

    private fun append(charCode: Int, categoryId: String) {
        val lastChar = ranges.lastOrNull()?.rangeEnd() ?: -1
        for (code in lastChar + 1 until charCode) {
            appendSingleChar(code, unassignedCategoryId)
        }
        appendSingleChar(charCode, categoryId)
    }

    private fun appendSingleChar(charCode: Int, categoryId: String) {
        if (shouldSkip(categoryId)) return

        if (ranges.isEmpty()) {
            ranges.add(createRange(charCode, categoryId))
            return
        }

        val lastRange = ranges.last()

        if (!lastRange.append(charCode, categoryId)) {
            val newLastRange = evolveLastRange(lastRange, charCode, categoryId)
            if (newLastRange != null) {
                ranges[ranges.lastIndex] = newLastRange
            } else {
                ranges.add(createRange(charCode, categoryId))
            }
        }
    }

    protected val unassignedCategoryId: String
        get() = categoryId(CharCategory.UNASSIGNED.code)


    private fun createRange(charCode: Int, categoryId: String): CategorizedRangePattern {
        return CategorizedPeriodicPattern.from(charCode, categoryId, sequenceLength = 1, isPeriodic = true, unassignedCategoryId, makeOnePeriodCategory)
    }

    /**
     * Removes the last char in the specified range.
     * Returns the simplest pattern that accommodated the remaining chars in the range,
     * or `null` if the range contained a single char.
     */
    private fun removeLast(range: CategorizedRangePattern): CategorizedRangePattern? {
        if (range.rangeLength() == 1) {
            return null
        }

        val rangeStart = range.rangeStart()
        var result: CategorizedRangePattern = createRange(rangeStart, range.categoryIdOf(rangeStart))
        for (code in rangeStart + 1 until range.rangeEnd()) {
            val categoryId = range.categoryIdOf(code)
            if (!shouldSkip(categoryId)) {
                result = if (result.append(code, categoryId)) result else evolveLastRange(result, code, categoryId)!!
            }
        }
        return result
    }

    protected abstract fun categoryId(categoryCode: String): String

    protected abstract fun shouldSkip(categoryId: String): Boolean

    protected abstract val makeOnePeriodCategory: (Array<String>) -> Int

    protected abstract fun evolveLastRange(
        lastRange: CategorizedRangePattern,
        charCode: Int,
        categoryId: String
    ): CategorizedRangePattern?
}