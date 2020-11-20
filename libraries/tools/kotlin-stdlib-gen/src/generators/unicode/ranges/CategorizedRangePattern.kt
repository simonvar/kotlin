/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode.ranges

import generators.unicode.*

internal interface CategorizedRangePattern {
    fun append(charCode: Int, categoryCode: String): CategorizedRangePattern?
    fun rangeStart(): Int
    fun rangeEnd(): Int
    fun category(): Int
}

private val categoryCodeToValue = CharCategory.values().associateBy({ it.code }, { it.value })

/**
 * A range of consequent chars having the same category
 */
internal class CategorizedConsequentPattern(
    val start: Int,
    val categoryCode: String
) : CategorizedRangePattern {
    var end = start
        private set

    fun setEnd(end: Int) {
        assert(this.end < end)
        this.end = end
    }

    override fun append(charCode: Int, categoryCode: String): CategorizedRangePattern? {
        if (charCode == end + 1 && categoryCode == this.categoryCode) {
            end = charCode
            return this
        }
        return CategorizedAlternatingPattern.from(this, charCode, categoryCode)
    }

    override fun rangeStart(): Int {
        return start
    }

    override fun rangeEnd(): Int {
        return end
    }

    override fun category(): Int {
        return categoryCodeToValue[categoryCode]!!
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
    private val start: Int,
    private var end: Int,
    internal val evenOddCategoryCodes: Array<String>
) : CategorizedRangePattern {
    override fun append(charCode: Int, categoryCode: String): CategorizedRangePattern? {
        if (charCode == end + 1 && categoryCode == evenOddCategoryCodes[charCode % 2]) {
            end = charCode
            return this
        }
        return CategorizedPeriodicTrioPattern.from(this, charCode, categoryCode)
    }

    override fun rangeStart(): Int {
        return start
    }

    override fun rangeEnd(): Int {
        return end
    }

    override fun category(): Int {
        val even = categoryCodeToValue[evenOddCategoryCodes[0]]!!
        val odd = categoryCodeToValue[evenOddCategoryCodes[1]]!!
        // Each category value is <= 30, thus 5 bits is enough to represent it. Use 10 bits to encode 2 categories.
        return (odd shl 5) or even
    }

    override fun toString(): String {
        return "CategorizedAlternatingPattern{" +
                "start=" + start.hex() +
                ", end=" + end.hex() +
                ", evenOddCategoryCodes=" + evenOddCategoryCodes.contentToString() +
                "}"
    }

    companion object {
        fun from(range: CategorizedConsequentPattern, charCode: Int, categoryCode: String): CategorizedAlternatingPattern? {
            if (charCode == range.start + 1) {
                check(categoryCode != range.categoryCode)
                val evenOddCategoryCodes = Array(2) { if (charCode % 2 == it) categoryCode else range.categoryCode }
                return CategorizedAlternatingPattern(range.start, charCode, evenOddCategoryCodes)
            }
            return null
        }
    }
}

/**
 * A range of consequent chars having alternating categories, e.g., [Lu, Ll, Lu, Ll, ...].
 */
internal class CategorizedPeriodicTrioPattern private constructor(
    private val start: Int,
    private var end: Int,
    private val categoryCodes: Array<String>
) : CategorizedRangePattern {
    override fun append(charCode: Int, categoryCode: String): CategorizedRangePattern? {
        if (charCode == end + 1 && categoryCode == categoryCodes[charCode % 3]) {
            end = charCode
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
        // Each category value is <= 30, thus 5 bits is enough to represent it. Use 15 bits to encode 3 categories.
        val two = categoryCodeToValue[categoryCodes[2]]!!
        val one = categoryCodeToValue[categoryCodes[1]]!!
        val zero = categoryCodeToValue[categoryCodes[0]]!!
        return (two shl 10) or (one shl 5) or zero
    }

    override fun toString(): String {
        return "CategorizedTrioPattern{" +
                "start=" + start.hex() +
                ", end=" + end.hex() +
                ", categoryCodes=" + categoryCodes.contentToString() +
                "}"
    }

    companion object {
        fun from(range: CategorizedAlternatingPattern, charCode: Int, categoryCode: String): CategorizedPeriodicTrioPattern? {
            if (charCode != range.rangeEnd() + 1) return null

            check(categoryCode != range.evenOddCategoryCodes[charCode % 2])

            val categoryCodes = arrayOf("", "", "")

            for (ch in range.rangeStart()..range.rangeEnd()) {
                val expected = categoryCodes[ch % 3]
                val actual = range.evenOddCategoryCodes[ch % 2]
                if (expected.isNotEmpty() && expected != actual) return null
                categoryCodes[ch % 3] = actual
            }
            val expected = categoryCodes[charCode % 3]
            if (expected.isNotEmpty() && expected != categoryCode) return null
            categoryCodes[charCode % 3] = categoryCode

            return CategorizedPeriodicTrioPattern(range.rangeStart(), charCode, categoryCodes)
        }
    }
}