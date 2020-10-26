/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators.unicode

import templates.COPYRIGHT_NOTICE
import templates.KotlinTarget
import templates.readCopyrightNoticeFromProfile
import java.io.File
import java.io.FileNotFoundException
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val unicodeDataFile: File
    val generators = mutableListOf<UnicodeDataGenerator>()

    when (args.size) {
        1 -> {
            val baseDir = File(args.first())

            unicodeDataFile = baseDir.resolve("libraries/tools/kotlin-stdlib-gen/src/generators/unicode/UnicodeData.txt")

            val categoryTestFile = baseDir.resolve("libraries/stdlib/js/test/text/unicodeData/_CharCategoryTest.kt")
            val categoryTestGenerator = CharCategoryTestGenerator(categoryTestFile)

            val jsCategoryRangesFile = baseDir.resolve("libraries/stdlib/js/src/generated/_CharCategories.kt")
            val jsCategoryRangesGenerator = CharCategoryRangesGenerator(jsCategoryRangesFile, KotlinTarget.JS)

            val jsIrCategoryRangesFile = baseDir.resolve("libraries/stdlib/js-ir/src/generated/_CharCategories.kt")
            val jsIrCategoryRangesGenerator = CharCategoryRangesGenerator(jsIrCategoryRangesFile, KotlinTarget.JS_IR)

            generators.add(categoryTestGenerator)
            generators.add(jsCategoryRangesGenerator)
            generators.add(jsIrCategoryRangesGenerator)
        }
        3 -> {
            val (unicodeDataPath, targetName, targetDir) = args

            unicodeDataFile = File(unicodeDataPath)

            val target = KotlinTarget.values.singleOrNull { it.name.equals(targetName, ignoreCase = true) }
                ?: error("Invalid target: $targetName")

            val categoryRangesFile = File(targetDir).resolve("_CharCategories.kt")
            val categoryRangesGenerator = CharCategoryRangesGenerator(categoryRangesFile, target)

            generators.add(categoryRangesGenerator)
        }
        else -> {
            println(
                """Parameters:
    <kotlin-base-dir> - generates UnicodeData.txt sources for js and js-ir targets using paths derived from specified base path
    <UnicodeData.txt-path> <target> <target-dir> - generates UnicodeData.txt sources for the specified target in the specified target directory
"""
            )
            exitProcess(1)
        }
    }

    if (!unicodeDataFile.exists()) {
        throw FileNotFoundException("Please place UnicodeData.txt at ${unicodeDataFile.parent}")
    }

    COPYRIGHT_NOTICE =
        readCopyrightNoticeFromProfile { Thread.currentThread().contextClassLoader.getResourceAsStream("apache.xml").reader() }

    unicodeDataFile.forEachLine { line ->
        val parts = line.split(";")
        if (parts[0].length <= 4) {
            generators.forEach { it.appendChar(parts[0], parts[1], parts[2]) }
        }
    }
    generators.forEach { it.close() }
}

internal interface UnicodeDataGenerator {
    fun appendChar(char: String, name: String, categoryCode: String)
    fun close()
}
