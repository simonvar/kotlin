/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.ranges.patterns

import generators.unicode.ranges.writers.hex

internal interface CategorizedRangePattern {
    fun append(charCode: Int, categoryCode: String): CategorizedRangePattern?
    fun prepend(charCode: Int, categoryCode: String): CategorizedRangePattern?
    fun removeLast(): CategorizedRangePattern?
    fun rangeStart(): Int
    fun rangeEnd(): Int
    fun category(): Int
    fun categoryCodeOf(charCode: Int): String
}

internal fun CategorizedRangePattern.rangeLength(): Int = rangeEnd() - rangeStart() + 1

// 17 and 31 category values are not reserved. Use 17 to replace UNASSIGNED value (0) to be able to encode range pattern categories.
internal const val UNASSIGNED_CATEGORY_VALUE_REPLACEMENT = 17
private val categoryCodeToValue = CharCategory.values().associateBy({ it.code }, { if (it.value == 0) UNASSIGNED_CATEGORY_VALUE_REPLACEMENT else it.value })

/**
 * A range of consequent chars having the same category
 */
internal class CategorizedConsequentPattern(
    private var start: Int,
    val categoryCode: String
) : CategorizedRangePattern {
    private var end = start

    fun setEnd(end: Int) {
        assert(this.end < end)
        this.end = end
    }

    override fun append(charCode: Int, categoryCode: String): CategorizedRangePattern? {
        require(charCode == end + 1)
        if (categoryCode == this.categoryCode) {
            end = charCode
            return this
        }
        return CategorizedAlternatingPattern.from(this, charCode, categoryCode)
    }

    override fun prepend(charCode: Int, categoryCode: String): CategorizedConsequentPattern? {
        require(charCode == start - 1)
        if (categoryCode == this.categoryCode) {
            start = charCode
            return this
        }
        return null
    }

    override fun removeLast(): CategorizedRangePattern? {
        if (rangeLength() > 1) {
            end--
            return this
        }
        return null
    }

    override fun rangeStart(): Int {
        return start
    }

    override fun rangeEnd(): Int {
        return end
    }

    override fun category(): Int {
        val value = categoryCodeToValue[categoryCode]!!
        check(value != 0)
        return value
    }

    override fun categoryCodeOf(charCode: Int): String {
        if (charCode !in start..end) {
            throw IllegalArgumentException("Char code ${charCode.hex()} is not in $this")
        }
        return categoryCode
    }

    override fun toString(): String {
        return "CategorizedConsequentPattern{" +
                "start=" + start.hex() +
                ", end=" + end.hex() +
                ", categoryCode=" + categoryCode +
                "}"
    }
}

/**
 * A range of consequent chars having alternating categories, e.g., [Lu, Ll, Lu, Ll, ...].
 */
internal class CategorizedAlternatingPattern private constructor(
    private var start: Int,
    private var end: Int,
    private val evenOddCategoryCodes: Array<String>
) : CategorizedRangePattern {
    override fun append(charCode: Int, categoryCode: String): CategorizedRangePattern? {
        require(charCode == end + 1)
        if (categoryCode == evenOddCategoryCodes[charCode % 2]) {
            end = charCode
            return this
        }
        return CategorizedPeriodicTrioPattern.from(this, charCode, categoryCode)
    }

    override fun prepend(charCode: Int, categoryCode: String): CategorizedAlternatingPattern? {
        require(charCode == start - 1)
        if (categoryCode == evenOddCategoryCodes[charCode % 2]) {
            start = charCode
            return this
        }
        return null
    }

    override fun removeLast(): CategorizedRangePattern? {
        if (rangeLength() > 2) {
            end--
            return this
        }
        return CategorizedConsequentPattern(start, categoryCodeOf(start))
    }

    override fun rangeStart(): Int {
        return start
    }

    override fun rangeEnd(): Int {
        return end
    }

    override fun category(): Int {
        // Each category value is <= 30, thus 5 bits is enough to represent it. Use 10 bits to encode 2 categories.
        val odd = categoryCodeToValue[evenOddCategoryCodes[1]]!!
        val even = categoryCodeToValue[evenOddCategoryCodes[0]]!!
        check(odd != 0)
        return (odd shl 5) or even
    }

    override fun categoryCodeOf(charCode: Int): String {
        if (charCode !in start..end) {
            throw IllegalArgumentException("Char code ${charCode.hex()} is not in $this")
        }
        return evenOddCategoryCodes[charCode % 2]
    }

    override fun toString(): String {
        return "CategorizedAlternatingPattern{" +
                "start=" + start.hex() +
                ", end=" + end.hex() +
                ", evenOddCategoryCodes=" + evenOddCategoryCodes.contentToString() +
                "}"
    }

    companion object {
        fun from(range: CategorizedRangePattern, charCode: Int, categoryCode: String): CategorizedRangePattern? {
            val evenOddCategoryCodes = arrayOf("", "")
            if (evenOddCategoryCodes.fill({ it % 2 }, range, charCode, categoryCode)) {
                return CategorizedAlternatingPattern(range.rangeStart(), charCode, evenOddCategoryCodes)
            }
            return CategorizedPeriodicTrioPattern.from(range, charCode, categoryCode)
        }
    }
}

/**
 * A range of consequent chars having three periodic categories, e.g., [Lu, Ll, Lo, Lu, ...].
 */
private class CategorizedPeriodicTrioPattern private constructor(
    private var start: Int,
    private var end: Int,
    private val categoryCodes: Array<String>
) : CategorizedRangePattern {
    override fun append(charCode: Int, categoryCode: String): CategorizedRangePattern? {
        require(charCode == end + 1)
        if (categoryCode == categoryCodes[charCode % 3]) {
            end = charCode
            return this
        }
        return null
    }

    override fun prepend(charCode: Int, categoryCode: String): CategorizedRangePattern? {
        require(charCode == start - 1)
        if (categoryCode == categoryCodes[charCode % 3]) {
            start = charCode
            return this
        }
        return null
    }

    override fun removeLast(): CategorizedRangePattern? {
        if (rangeLength() > 3) {
            end--
            return this
        }
        return CategorizedConsequentPattern(start, categoryCodeOf(start)).append(start + 1, categoryCodeOf(start + 1))!!
    }

    override fun rangeStart(): Int {
        return start
    }

    override fun rangeEnd(): Int {
        return end
    }

    override fun category(): Int {
        // Each category value is <= 30, thus 5 bits is enough to represent it. Use 15 bits to encode 3 categories.
        val two = categoryCodeToValue[categoryCodes[2]]!!
        val one = categoryCodeToValue[categoryCodes[1]]!!
        val zero = categoryCodeToValue[categoryCodes[0]]!!
        check(two != 0)
        return (two shl 10) or (one shl 5) or zero
    }

    override fun categoryCodeOf(charCode: Int): String {
        if (charCode !in start..end) {
            throw IllegalArgumentException("Char code ${charCode.hex()} is not in $this")
        }
        return categoryCodes[charCode % 3]
    }

    override fun toString(): String {
        return "CategorizedTrioPattern{" +
                "start=" + start.hex() +
                ", end=" + end.hex() +
                ", categoryCodes=" + categoryCodes.contentToString() +
                "}"
    }

    companion object {
        fun from(range: CategorizedRangePattern, charCode: Int, categoryCode: String): CategorizedPeriodicTrioPattern? {
            val categoryCodes = arrayOf("", "", "")
            if (categoryCodes.fill({ it % 3 }, range, charCode, categoryCode)) {
                return CategorizedPeriodicTrioPattern(range.rangeStart(), charCode, categoryCodes)
            }
            return null
        }
    }
}

private fun Array<String>.fill(
    indexOfCharCode: (Int) -> Int,
    range: CategorizedRangePattern,
    charCode: Int,
    categoryCode: String
): Boolean {
    fun fill(charCode: Int, categoryCode: String): Boolean {
        val expected = this[indexOfCharCode(charCode)]
        if (expected.isNotEmpty() && expected != categoryCode) return false
        this[indexOfCharCode(charCode)] = categoryCode
        return true
    }

    for (ch in range.rangeStart()..range.rangeEnd()) {
        if (!fill(ch, range.categoryCodeOf(ch))) return false
    }
    for (ch in range.rangeEnd() + 1 until charCode) {
        if (!fill(ch, CharCategory.UNASSIGNED.code)) return false
    }
    if (!fill(charCode, categoryCode)) return false

    return true
}