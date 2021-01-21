/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.reporting

class KotlinImportingReportsContainer {
    val reports = mutableSetOf<KotlinImportingReport>()

    operator fun plusAssign(report: KotlinImportingReport) {
        reports += report
    }

    inline operator fun <reified T : KotlinImportingReport> get(vararg reportClasses: Class<out T>): List<T> {
        return reportClasses.flatMap { reports.filterIsInstance(it) }
    }
}