// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.module

import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModulesContains
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ModuleAssertionTest : ModuleAssertionTestCase() {

  @Test
  fun `test ModuleAssertions#assertModules`() {
    runBlocking {
      project.workspaceModel.update {
        addModuleEntity("project", ".")
        addModuleEntity("project.module1", "module1")
        addModuleEntity("project.module2", "module2")
      }
      assertModules(project, "project", "project.module1", "project.module2")
      assertModules(project, "project", "project.module2", "project.module1")
      Assertions.assertThrows(AssertionError::class.java) {
        assertModules(project, "project.module1", "project.module2")
      }
      Assertions.assertThrows(AssertionError::class.java) {
        assertModules(project, "project", "module1", "module2")
      }
      Assertions.assertThrows(AssertionError::class.java) {
        assertModules(project, "project", "other-module")
      }
    }
  }

  @Test
  fun `test ModuleAssertions#assertModulesContains`() {
    runBlocking {
      project.workspaceModel.update {
        addModuleEntity("project", ".")
        addModuleEntity("project.module1", "module1")
        addModuleEntity("project.module2", "module2")
      }
      assertModulesContains(project, "project", "project.module1", "project.module2")
      assertModulesContains(project, "project", "project.module2", "project.module1")
      assertModulesContains(project, "project.module1", "project.module2")
      Assertions.assertThrows(AssertionError::class.java) {
        assertModulesContains(project, "project", "module1", "module2")
      }
      Assertions.assertThrows(AssertionError::class.java) {
        assertModulesContains(project, "project", "other-module")
      }
    }
  }
}