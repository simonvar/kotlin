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

/**
 * This program generates sources related to UnicodeData.txt.
 * There are two ways to run the program.
 * 1. Pass the root directory of the project to generate sources for js and js-ir.
 *  _CharCategoryTest.kt and supporting files are also generated to test the generated sources.
 *  The UnicodeData.txt file must be placed in the same directory where this file is localed,
 *  i.e., rootDir/libraries/tools/kotlin-stdlib-gen/src/generators/unicode/
 *  The generated test is meant to be run after updating Unicode version and should not be merged to master.
 * 2. Pass the path to the UnicodeData.txt, name of the target to generate sources for, and the directory to generate sources in.
 *  No tests are generated.
 */
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
