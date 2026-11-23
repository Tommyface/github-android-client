/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * 门禁 6：架构约定测试。
 * 规则对应《技术架构设计》中的「依赖铁律」。
 */
class ArchitectureTest {
    @Test
    fun domainLayerDoesNotDependOnAndroidFramework() {
        Konsist
            .scopeFromDirectory("core/domain/src/main/kotlin")
            .files
            .assertTrue { file -> !file.hasImportWithName("android.content.Context", "android.app.Activity") }
    }

    @Test
    fun viewModelShouldResideInPresentationPackage() {
        Konsist
            .scopeFromDirectory(".")
            .files
            .assertTrue { file ->
                if (file.name.endsWith("ViewModel.kt")) {
                    file.path.contains("presentation")
                } else {
                    true
                }
            }
    }
}
