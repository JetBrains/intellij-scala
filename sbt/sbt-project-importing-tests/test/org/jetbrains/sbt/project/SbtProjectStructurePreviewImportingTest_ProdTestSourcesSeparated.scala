package org.jetbrains.sbt.project

import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.sbt.project.SbtProjectResolver.PreviewImportNumberSuffixInTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests]))
class SbtProjectStructurePreviewImportingTest_ProdTestSourcesSeparated extends SbtProjectStructureImportingLike {

  import ProjectStructureDsl._

  override protected def enableSeparateModulesForProdTest = true

  override protected def isPreview = true

  // https://youtrack.jetbrains.com/issue/SCL-24181
  def testSimplePreviewWithoutProjectDir(): Unit =
    runSimplePreviewTest(projectName = "simplepreviewwithoutprojectdir", buildModuleSources = Nil)

  // https://youtrack.jetbrains.com/issue/SCL-24181
  def testSimplePreviewWithProjectDir(): Unit =
    runSimplePreviewTest(projectName = "simplepreviewwithprojectdir", buildModuleSources = Seq("%PROJECT_ROOT%/project"))

  def runSimplePreviewTest(projectName: String, buildModuleSources: Seq[String]): Unit =
    runTest(
      new project(projectName) {
        libraries := Nil

        modules := Seq(
          new module(s"${projectName}_$PreviewImportNumberSuffixInTests") {
            contentRoots += getProjectPath
            excluded := Seq("target")
          },
          new module(s"${projectName}_$PreviewImportNumberSuffixInTests.main") {
            contentRoots := Seq(s"$getProjectPath/src/main")
            sources := Seq("scala")
            resources := Nil
            testSources := Nil
            testResources:= Nil
            libraryDependencies := Nil
          },
          new module(s"${projectName}_$PreviewImportNumberSuffixInTests.test") {
            contentRoots := Nil
            sources := Nil
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies := Nil
          },
          new module(s"${projectName}_$PreviewImportNumberSuffixInTests.${projectName}_$PreviewImportNumberSuffixInTests-build") {
            sources := buildModuleSources
            excluded := Seq("project/target", "target")
          }
        )
      }
    )
}
