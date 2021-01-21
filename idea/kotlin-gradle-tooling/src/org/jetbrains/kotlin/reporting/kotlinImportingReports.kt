/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.reporting

import java.io.Serializable

sealed class KotlinImportingReport : Serializable

data class OrphanSourceSetsImportingReport(val projectName: String, val sourceSetName: String) : KotlinImportingReport() {
    val message: String
        get() = "[sync warning] Source set \"$sourceSetName\" is not compiled with any compilation. This source set is not imported in the IDE."
}

data class ErroneousImportingReport(val cause: String, @Transient val throwable: Throwable? = null) : KotlinImportingReport()

inline fun <reified T : KotlinImportingReport> T.addAndProcess(
    reportsContainer: KotlinImportingReportsContainer,
    processor: GradleLogImportingReportProcessor<T>
) {
    processor.process(this)
    reportsContainer += this
}