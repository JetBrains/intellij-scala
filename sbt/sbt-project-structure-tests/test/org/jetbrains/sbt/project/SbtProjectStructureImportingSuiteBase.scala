package org.jetbrains.sbt.project

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.sbt.SbtVersion
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.experimental.categories.Category

/**
 * Full project-structure importing suite for the default separate main/test module layout.
 *
 * Extend this class only when a variant should run the whole suite with different import settings, such as sbt shell import.
 * Tests that only need project-structure importing utilities should extend [[SbtProjectStructureImportingTestBase]] instead.
 *
 * @see [[SbtProjectStructureImportingTest]]
 * @see [[SbtShellProjectStructureImportingTestBase]]
 * @see [[SbtProjectStructureImportingTest_LegacyModulesLayout]]
 * @todo ensure there is a test for SCL-19673 for the BSP external system as well
 */
@Category(Array(classOf[SlowTests]))
abstract class SbtProjectStructureImportingSuiteBase extends SbtProjectStructureImportingTestBase {

  import ProjectStructureDsl.*

  /**
   * Resolves the test data project directory path.
   *
   * This method strips the `_sbt_*` version suffix from the test method name.
   * This allows multiple version-specific tests (e.g., [[testSimpleTwoBuilds_sbt_1_12_1]]) to share the same underlying test data directory.
   */
  override protected def getTestDataProjectPath: String = {
    val testName = getTestName(true).replaceAll("_sbt_.*$", "")
    generateTestProjectPath(testName)
  }

  protected def runSimpleTest(
    projectName: String,
    scalaVersion: String,
    expectedScalaLibraries: Seq[library],
    expectedSbtCompletionVariantsForParentModule: Seq[ExpectedDirectoryCompletionVariant] = DefaultSbtContentRootsScala213,
    expectedSbtCompletionVariantsForMainModule: Seq[ExpectedDirectoryCompletionVariant] = DefaultMainSbtContentRootsScala213,
    expectedSbtCompletionVariantsForTestModule: Seq[ExpectedDirectoryCompletionVariant] = DefaultTestSbtContentRootsScala213,
    mutedNotificationTitles: Seq[String] = Seq.empty
  ): Unit = {
    runTest(
      new project(projectName) {
        libraries := expectedScalaLibraries

        modules := Seq(
          new module(projectName) {
            contentRoots += getProjectPath
            excluded := Seq("target")
          },
          new module(s"$projectName.main") {
            contentRoots := Seq(s"$getProjectPath/src/main", s"$getProjectPath/target/scala-$scalaVersion/src_managed/main", s"$getProjectPath/target/scala-$scalaVersion/resource_managed/main")
            sources := Seq("scala", "java")
            resources := Seq("resources")
            testSources := Nil
            testResources:= Nil
            libraryDependencies := expectedScalaLibraries
          },
          new module(s"$projectName.test") {
            contentRoots := Seq(s"$getProjectPath/src/test", s"$getProjectPath/target/scala-$scalaVersion/src_managed/test", s"$getProjectPath/target/scala-$scalaVersion/resource_managed/test")
            sources := Nil
            resources := Nil
            testSources := Seq("scala", "java")
            testResources := Seq("resources")
            libraryDependencies := expectedScalaLibraries
          },
          new module(s"$projectName.$projectName-build") {
            sources := Seq("")
            excluded := Seq("project/target", "target")
          }
        )
      },
      identity,
      mutedNotificationTitles = mutedNotificationTitles
    )
    assertDirectoryCompletionVariantsForProjectPaths(
      expectedSbtCompletionVariantsForParentModule,
      expectedSbtCompletionVariantsForMainModule,
      expectedSbtCompletionVariantsForTestModule,
      getMyProject.baseDir.getPath
    )
  }
}
