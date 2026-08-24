package org.jetbrains.sbt.project

import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.sbt.project.ProjectStructureDsl.*
import org.jetbrains.sbt.project.runner.SbtProjectStructureImportingRunner
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_SimpleSbt013 extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def simpleSbt013(): Unit =
    simpleSbtIvyBasedTest(mutedNotificationTitles = Seq("Legacy sbt version 0.13.18 detected"))

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCases_SimpleSbt104 extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def simpleSbt104(): Unit =
    simpleSbtIvyBasedTest()

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCases_SimpleSbt116 extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def simpleSbt116(): Unit =
    simpleSbtIvyBasedTest()

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCases_SimpleSbt128 extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def simpleSbt128(): Unit =
    simpleSbtIvyBasedTest()

abstract class SbtProjectStructureImportingTestCasesUtilities extends SbtProjectStructureImportingTestCase:
  // Duplicated code from SbtProjectStructureImportingSuiteBase. Will be removed after all test cases have been migrated
  // to this new running scheme.
  protected def simpleSbtIvyBasedTest(mutedNotificationTitles: Seq[String] = Seq.empty): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkFromIvy(useEnv = true)("2.12.10")

    runSimpleTest("simple", "2.12", scalaLibraries,
      expectedSbtCompletionVariantsForParentModule = customSbtContentRootsForParent(12),
      expectedSbtCompletionVariantsForMainModule = customSbtContentRootsForMain(12),
      expectedSbtCompletionVariantsForTestModule = customSbtContentRootsForTest(12),
      mutedNotificationTitles = mutedNotificationTitles
    )

    // Adding the assertion here not to create a separate heavy test for such a tiny check
    // org.jetbrains.plugins.scala.project.ProjectExt#modulesWithScala
    assertEquals(
      "modulesWithScala should return list of non *-build modules",
      Seq("simple.test", "simple.main"),
      getMyProject.modulesWithScala.map(_.getName),
    )

    val expectedLineInProcessOutput = "[error] Some error message which shouldn't fail the whole build, see SCL-21478 and SCL-13038"
    assertTrue(
      s"Can't find this line in sbt process output during sbt structure extraction:\n$expectedLineInProcessOutput",
      SbtProjectResolver.getProcessOutputOfLatestStructureDump.contains(expectedLineInProcessOutput)
    )
  }

  private def runSimpleTest(
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
            testResources := Nil
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

  private def customSbtContentRootsForParent(binaryVersion: Int): Seq[ExpectedDirectoryCompletionVariant] =
    Seq(
      ("src/main/java", JavaSourceRootType.SOURCE),
      ("src/main/scala", JavaSourceRootType.SOURCE),
      (s"src/main/scala-2.$binaryVersion", JavaSourceRootType.SOURCE),
      ("src/test/java", JavaSourceRootType.TEST_SOURCE),
      ("src/test/scala", JavaSourceRootType.TEST_SOURCE),
      (s"src/test/scala-2.$binaryVersion", JavaSourceRootType.TEST_SOURCE),
      ("src/main/resources", JavaResourceRootType.RESOURCE),
      ("src/test/resources", JavaResourceRootType.TEST_RESOURCE),
    ).map(ExpectedDirectoryCompletionVariant.apply.tupled)

  private def customSbtContentRootsForMain(binaryVersion: Int): Seq[ExpectedDirectoryCompletionVariant] =
    Seq(
      ("java", JavaSourceRootType.SOURCE),
      ("scala", JavaSourceRootType.SOURCE),
      (s"scala-2.$binaryVersion", JavaSourceRootType.SOURCE),
      ("resources", JavaResourceRootType.RESOURCE),
    ).map(ExpectedDirectoryCompletionVariant.apply.tupled)

  private def customSbtContentRootsForTest(binaryVersion: Int): Seq[ExpectedDirectoryCompletionVariant] =
    Seq(
      ("java", JavaSourceRootType.TEST_SOURCE),
      ("scala", JavaSourceRootType.TEST_SOURCE),
      (s"scala-2.$binaryVersion", JavaSourceRootType.TEST_SOURCE),
      ("resources", JavaResourceRootType.TEST_RESOURCE),
    ).map(ExpectedDirectoryCompletionVariant.apply.tupled)
