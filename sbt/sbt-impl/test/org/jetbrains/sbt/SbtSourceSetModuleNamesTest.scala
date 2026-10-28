package org.jetbrains.sbt

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.{FixturesKt, TestFixture}
import org.jetbrains.plugins.scala.util.SbtModuleType
import org.jetbrains.sbt.SbtSourceSetUtil.SbtSourceSetModuleExt
import org.jetbrains.sbt.project.settings.DisplayModuleName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * How [[SbtSourceSetUtil]] names real modules: the source set suffix is only removed from sbt source set modules,
 * which it tells by their external module type.
 */
@TestApplication
class SbtSourceSetModuleNamesTest:

  /** Fixtures must be class members to be set up before and torn down after each test. */
  private val projectFixture: TestFixture[Project] = FixturesKt.projectFixture()
  private val mainFixture: TestFixture[Module] = FixturesKt.moduleFixture(projectFixture, "root.C.main")
  private val numberedTestFixture: TestFixture[Module] = FixturesKt.moduleFixture(projectFixture, "root.C.test~1")
  private val plainFixture: TestFixture[Module] = FixturesKt.moduleFixture(projectFixture, "lib.main")

  private def sourceSetModule(fixture: TestFixture[Module], displayName: String | Null): Module =
    val module = fixture.get()
    WriteAction.runAndWait: () =>
      ExternalSystemModulePropertyManager.getInstance(module).setExternalModuleType(SbtModuleType.sbtSourceSetModuleType)
    DisplayModuleName.getInstance(module).setName(displayName)
    module

  @Test
  def sourceSetModuleIsNamedWithoutItsSourceSet(): Unit =
    assertEquals("root.C", sourceSetModule(mainFixture, "C.main").nameWithoutSourceSet)

  @Test
  def sourceSetModuleIsShownByItsSbtProjectName(): Unit =
    assertEquals(Some("C"), sourceSetModule(mainFixture, "C.main").displayNameWithoutSourceSet)

  @Test
  def numberedSourceSetModuleIsNamedWithoutItsSourceSet(): Unit =
    val module = sourceSetModule(numberedTestFixture, "C.test")
    assertEquals("root.C", module.nameWithoutSourceSet)
    assertEquals(Some("C"), module.displayNameWithoutSourceSet)

  @Test
  def otherModuleKeepsANameEndingLikeASourceSet(): Unit =
    val module = plainFixture.get()
    DisplayModuleName.getInstance(module).setName("lib.main")
    assertEquals("lib.main", module.nameWithoutSourceSet)
    assertEquals(Some("lib.main"), module.displayNameWithoutSourceSet)

  @Test
  def moduleWithoutDisplayNameHasNoSbtProjectName(): Unit =
    assertEquals(None, sourceSetModule(mainFixture, null).displayNameWithoutSourceSet)

  @Test
  def moduleWithEmptyDisplayNameHasNoSbtProjectName(): Unit =
    assertEquals(None, sourceSetModule(mainFixture, "").displayNameWithoutSourceSet)
