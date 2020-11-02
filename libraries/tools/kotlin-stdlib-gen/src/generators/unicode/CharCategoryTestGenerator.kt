/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode

import java.io.File
import java.io.FileWriter

internal class CharCategoryTestGenerator(private val outputFile: File) : UnicodeDataGenerator {
    private var arrayIndex = 0
    private var arraySize = 0
    private var writer: FileWriter? = null

    init {
        outputFile.parentFile.mkdirs()
    }

    override fun appendChar(char: String, name: String, categoryCode: String) {
        if (arraySize == 0) {
            writer?.appendLine(")")
            writer?.close()

            generateUnicodeDataHeader(arrayIndex)
        }

        val isStart = name.endsWith(", First>")

        writer?.appendLine("    CharProperties(char = '\\u$char', isStartOfARange = $isStart, categoryCode = \"$categoryCode\"),")

        arraySize++
        if (arraySize == 2048) {
            arraySize = 0
            arrayIndex++
        }
    }

    override fun close() {
        writer?.appendLine(")")
        writer?.close()

        generateFlattenUnicodeData()
        generateCharProperties()
        generateCharCategoryTest()
    }

    private fun generateFlattenUnicodeData() {
        val file = outputFile.resolveSibling("_UnicodeDataFlatten.kt")
        generateFileHeader(file)

        writer?.appendLine("internal val unicodeData = arrayOf<Array<CharProperties>>(")
        for (index in 0..arrayIndex) {
            writer?.appendLine("    unicodeData$index,")
        }
        writer?.appendLine(").flatten()")

        writer?.close()
    }

    private fun generateCharProperties() {
        val file = outputFile.resolveSibling("_CharProperties.kt")
        generateFileHeader(file)

        writer?.appendLine("data class CharProperties(val char: Char, val isStartOfARange: Boolean, val categoryCode: String)")
        writer?.close()
    }

    private fun generateCharCategoryTest() {
        generateFileHeader(outputFile)

        writer?.appendLine(
            """
import kotlin.test.*

class CharCategoryTest {
    @Test
    fun category() {
        val charProperties = hashMapOf<Char, CharProperties>()

        for (properties in unicodeData) {
            charProperties[properties.char] = properties
        }

        var properties: CharProperties? = null

        for (char in Char.MIN_VALUE..Char.MAX_VALUE) {
            if (charProperties.containsKey(char)) {
                properties = charProperties.getValue(char)
            } else if (properties?.isStartOfARange != true) {
                properties = null
            }

            val charCode = char.toInt().toString(radix = 16).padStart(length = 4, padChar = '0')
            val expectedCategoryCode = properties?.categoryCode ?: CharCategory.UNASSIGNED.code

            assertEquals(
                expected = expectedCategoryCode,
                actual = char.category.code,
                "Char:[${"$"}char] with code:[${"$"}charCode] has category [${"$"}expectedCategoryCode] in Unicode, but [${"$"}{char.category.code}] in Kotlin"
            )

            val expectedIsDigit = isDigit(expectedCategoryCode)
            val expectedIsLetter = isLetter(expectedCategoryCode)
            val expectedIsLetterOrDigit = expectedIsLetter || expectedIsDigit

            assertEquals(
                expected = expectedIsDigit,
                actual = char.isDigit(),
                "Char:[${"$"}char] with code:[${"$"}charCode] in Unicode has isDigit() = ${"$"}{expectedIsDigit}, but in Kotlin isDigit() = ${"$"}{char.isDigit()}"
            )
            assertEquals(
                expected = expectedIsLetter,
                actual = char.isLetter(),
                "Char:[${"$"}char] with code:[${"$"}charCode] in Unicode has isLetter() = ${"$"}{expectedIsLetter}, but in Kotlin isLetter() = ${"$"}{char.isLetter()}"
            )
            assertEquals(
                expected = expectedIsLetterOrDigit,
                actual = char.isLetterOrDigit(),
                "Char.isLetterOrDigit() should be true if Char.isLetter() or Char.isDigit() is true"
            )
        }
    }

    private fun isDigit(categoryCode: String): Boolean {
        return CharCategory.DECIMAL_DIGIT_NUMBER.code == categoryCode
    }

    private fun isLetter(categoryCode: String): Boolean {
        return categoryCode in listOf(
            CharCategory.UPPERCASE_LETTER,
            CharCategory.LOWERCASE_LETTER,
            CharCategory.TITLECASE_LETTER,
            CharCategory.MODIFIER_LETTER,
            CharCategory.OTHER_LETTER
        ).map { it.code }
    }
}
            """.trimIndent()
        )

        writer?.close()
    }

    private fun generateUnicodeDataHeader(arrayIndex: Int) {
        val file = outputFile.resolveSibling("_UnicodeData$arrayIndex.kt")
        generateFileHeader(file)

        writer?.appendLine("internal val unicodeData$arrayIndex = arrayOf<CharProperties>(")
    }

    private fun generateFileHeader(file: File) {
        writer = FileWriter(file)
        writer?.writeHeader(file, "test.text.unicodeData")
        writer?.appendLine()
    }
}
