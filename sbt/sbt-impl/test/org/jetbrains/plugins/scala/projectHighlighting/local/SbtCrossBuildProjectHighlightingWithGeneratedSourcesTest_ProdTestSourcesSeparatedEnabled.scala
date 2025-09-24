package org.jetbrains.plugins.scala.projectHighlighting.local

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.projectHighlighting.base.SbtProjectHighlightingLocalProjectsTestBase
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.sbt.project.ProjectStructureDsl.{contentRoots, module}
import org.jetbrains.sbt.project.{ProjectStructureDsl, ProjectStructureMatcher}
import org.jetbrains.sbt.project.utils.ProjectStructureComparisonContext

class SbtCrossBuildProjectHighlightingWithGeneratedSourcesTest_ProdTestSourcesSeparatedEnabled extends SbtProjectHighlightingLocalProjectsTestBase {

  override def projectName = "sbt-crossproject-test-project-with-generated-sources"

  override protected def highlightSingleFile(
    virtualFile: VirtualFile,
    psiFile: PsiFile,
    reporter: HighlightingProgressReporter,
  ): Unit =
    doHighlightingForFile(virtualFile, psiFile, reporter)

  private class myModule(
    name: String,
    group: Array[String] = Array.empty,
  ) extends module(name, group) {
    import ProjectStructureDsl._
    locally {
      contentRoots := Seq()
      sources := Seq()
      testSources := Seq()
      moduleDependencies := Seq()
      excluded := Seq()
      // NOTE: we don't test resources directories as they should behave similar to sources/testSources
      // We comment them out to avoid too much test data.
      // We could bring it back once it becomes essential for some use cases
      //resources := Seq()
      //testResources := Seq()
    }
  }

  private def standardRoots(m: module, scope: String, scalaVersionMajor: String): Unit = {
    import m._
    contentRoots := Seq(
      s"%PROJECT_ROOT%/src/$scope",
      s"%PROJECT_ROOT%/target/scala-2.12/src_managed/$scope",
      s"%PROJECT_ROOT%/target/scala-2.12/resource_managed/$scope"
    )

    val sources = Seq(
      s"%PROJECT_ROOT%/src/$scope/java",
      s"%PROJECT_ROOT%/src/$scope/scala",
      s"%PROJECT_ROOT%/src/$scope/scala-2",
      s"%PROJECT_ROOT%/src/$scope/scala-$scalaVersionMajor",
      s"%PROJECT_ROOT%/target/scala-$scalaVersionMajor/src_managed/$scope",
    )

    if (scope == "test")
      ProjectStructureDsl.testSources := sources
    else
      ProjectStructureDsl.sources := sources
  }

  private def standardRootsForSharedPureModule(m: module, moduleBaseName: String, scope: String, scalaVersionMajor: String): Unit = {
    import m._
    contentRoots := Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope"
    )
    val sources = Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala-2",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala-$scalaVersionMajor",
    )

    if (scope == "test")
      ProjectStructureDsl.testSources := sources
    else
      ProjectStructureDsl.sources := sources
  }

  private def standardRootsForSharedFullModule(m: module, moduleBaseName: String, scope: String, scalaVersionMajor: String): Unit = {
    import m._
    contentRoots := Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/shared/src/$scope"
    )
    val sources = Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/shared/src/$scope/scala",
      s"%PROJECT_ROOT%/$moduleBaseName/shared/src/$scope/scala-2",
      s"%PROJECT_ROOT%/$moduleBaseName/shared/src/$scope/scala-$scalaVersionMajor",
    )

    if (scope == "test")
      ProjectStructureDsl.testSources := sources
    else
      ProjectStructureDsl.sources := sources
  }

  private def standardRootsForPureCrossModule(m: module, moduleBaseName: String, scope: String, scalaVersionMajor: String, platform: String): Unit = {
    import m._
    contentRoots := Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/.$platform/src/$scope",
      s"%PROJECT_ROOT%/$moduleBaseName/.$platform/target/scala-$scalaVersionMajor/src_managed/$scope",
      s"%PROJECT_ROOT%/$moduleBaseName/.$platform/target/scala-$scalaVersionMajor/resource_managed/$scope",
    )
    val sources = Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/.$platform/src/$scope/java",
      s"%PROJECT_ROOT%/$moduleBaseName/.$platform/src/$scope/scala",
      s"%PROJECT_ROOT%/$moduleBaseName/.$platform/src/$scope/scala-2",
      s"%PROJECT_ROOT%/$moduleBaseName/.$platform/src/$scope/scala-$scalaVersionMajor",
      s"%PROJECT_ROOT%/$moduleBaseName/.$platform/target/scala-$scalaVersionMajor/src_managed/$scope",
    )

    if (scope == "test")
      ProjectStructureDsl.testSources := sources
    else
      ProjectStructureDsl.sources := sources
  }

  private def standardRootsForFullCrossModule(m: module, moduleBaseName: String, scope: String, scalaVersionMajor: String, platform: String): Unit = {
    import m._
    contentRoots := Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/$platform/src/$scope",
      s"%PROJECT_ROOT%/$moduleBaseName/$platform/target/scala-$scalaVersionMajor/src_managed/$scope",
      s"%PROJECT_ROOT%/$moduleBaseName/$platform/target/scala-$scalaVersionMajor/resource_managed/$scope",
    )
    val sources = Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/$platform/src/$scope/java",
      s"%PROJECT_ROOT%/$moduleBaseName/$platform/src/$scope/scala",
      s"%PROJECT_ROOT%/$moduleBaseName/$platform/src/$scope/scala-2",
      s"%PROJECT_ROOT%/$moduleBaseName/$platform/src/$scope/scala-$scalaVersionMajor",
      s"%PROJECT_ROOT%/$moduleBaseName/$platform/target/scala-$scalaVersionMajor/src_managed/$scope"
    )

    if (scope == "test")
      ProjectStructureDsl.testSources := sources
    else
      ProjectStructureDsl.sources := sources
  }

  //noinspection ScalaUnusedSymbol,TypeAnnotation
  def testProjectStructure(): Unit = {
    import org.jetbrains.sbt.project.ProjectStructureDsl._

    val expectedProject: project = new project(projectName) {
      val rootModule = new myModule(projectName) {
        contentRoots := Seq("%PROJECT_ROOT%")
        excluded := Seq("target")
      }
      val rootModuleMain = new myModule(s"$projectName.main") {
        standardRoots(this, "main", "2.12")
      }
      val rootModuleTest = new myModule(s"$projectName.test") {
        standardRoots(this, "test", "2.12")
      }
      rootModule.dependsOn(rootModuleMain, rootModuleTest)
      rootModuleTest.dependsOn(rootModuleMain)

      val rootBuild = new myModule(s"$projectName.$projectName-build") {
        contentRoots := Seq("%PROJECT_ROOT%/project")
        sources := Seq("%PROJECT_ROOT%/project")
        excluded := Seq("target", "project/target")
      }

      val upstreamPureGroup = Array(projectName, "upstreamPure")
      val upstreamPureSources = new myModule("upstreamPure-sources", upstreamPureGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/upstreamPure")
        excluded := Seq("target")
      }
      val upstreamPureSourcesMain = new myModule("upstreamPure-sources.main", upstreamPureGroup) {
        standardRootsForSharedPureModule(this, "upstreamPure", "main", "2.13")
      }
      val upstreamPureSourcesTest = new myModule("upstreamPure-sources.test", upstreamPureGroup) {
        standardRootsForSharedPureModule(this, "upstreamPure", "test", "2.13")
      }
      val upstreamPureJS = new myModule("upstreamPureJS", upstreamPureGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/upstreamPure/.js")
        excluded := Seq("target")
      }
      val upstreamPureJSMain = new myModule("upstreamPureJS.main", upstreamPureGroup) {
        standardRootsForPureCrossModule(this, "upstreamPure", "main", "2.13", "js")
      }
      val upstreamPureJSTest = new myModule("upstreamPureJS.test", upstreamPureGroup) {
        standardRootsForPureCrossModule(this, "upstreamPure", "test", "2.13", "js")
      }
      val upstreamPureJVM = new myModule("upstreamPureJVM", upstreamPureGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/upstreamPure/.jvm")
        excluded := Seq("target")
      }
      val upstreamPureJVMMain = new myModule("upstreamPureJVM.main", upstreamPureGroup) {
        standardRootsForPureCrossModule(this, "upstreamPure", "main", "2.13", "jvm")
      }
      val upstreamPureJVMTest = new myModule("upstreamPureJVM.test", upstreamPureGroup) {
        standardRootsForPureCrossModule(this, "upstreamPure", "test", "2.13", "jvm")
      }
      upstreamPureSources.dependsOn(upstreamPureSourcesMain, upstreamPureSourcesTest)
      upstreamPureSourcesTest.dependsOn(upstreamPureJVMMain)
      upstreamPureJS.dependsOn(upstreamPureJSMain, upstreamPureJSTest)
      upstreamPureJSMain.dependsOn(upstreamPureSourcesMain)
      upstreamPureJSTest.dependsOn(upstreamPureJSMain, upstreamPureSourcesMain, upstreamPureSourcesTest)
      upstreamPureJVM.dependsOn(upstreamPureJVMMain, upstreamPureJVMTest)
      upstreamPureJVMMain.dependsOn(upstreamPureSourcesMain)
      upstreamPureJVMTest.dependsOn(upstreamPureJVMMain, upstreamPureSourcesMain, upstreamPureSourcesTest)

      val downstreamPureGroup = Array(projectName, "downstreamPure")
      val downstreamPureSources = new myModule("downstreamPure-sources", downstreamPureGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/downstreamPure")
        excluded := Seq("target")
      }
      val downstreamPureSourcesMain = new myModule("downstreamPure-sources.main", downstreamPureGroup) {
        standardRootsForSharedPureModule(this, "downstreamPure", "main", "2.13")
      }
      val downstreamPureSourcesTest = new myModule("downstreamPure-sources.test", downstreamPureGroup) {
        standardRootsForSharedPureModule(this, "downstreamPure", "test", "2.13")
      }
      val downstreamPureJS = new myModule("downstreamPureJS", downstreamPureGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/downstreamPure/.js")
        excluded := Seq("target")
      }
      val downstreamPureJSMain = new myModule("downstreamPureJS.main", downstreamPureGroup) {
        standardRootsForPureCrossModule(this, "downstreamPure", "main", "2.13", "js")
      }
      val downstreamPureJSTest = new myModule("downstreamPureJS.test", downstreamPureGroup) {
        standardRootsForPureCrossModule(this, "downstreamPure", "test", "2.13", "js")
      }
      val downstreamPureJVM = new myModule("downstreamPureJVM", downstreamPureGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/downstreamPure/.jvm")
        excluded := Seq("target")
      }
      val downstreamPureJVMMain = new myModule("downstreamPureJVM.main", downstreamPureGroup) {
        standardRootsForPureCrossModule(this, "downstreamPure", "main", "2.13", "jvm")
      }
      val downstreamPureJVMTest = new myModule("downstreamPureJVM.test", downstreamPureGroup) {
        standardRootsForPureCrossModule(this, "downstreamPure", "test", "2.13", "jvm")
      }
      downstreamPureSources.dependsOn(downstreamPureSourcesMain, downstreamPureSourcesTest)
      downstreamPureSourcesMain.dependsOn(upstreamPureJVMMain)
      downstreamPureSourcesTest.dependsOn(upstreamPureJVMMain, downstreamPureJVMMain)
      downstreamPureJS.dependsOn(downstreamPureJSMain, downstreamPureJSTest)
      downstreamPureJSMain.dependsOn(downstreamPureSourcesMain, upstreamPureJSMain, upstreamPureSourcesMain)
      downstreamPureJSTest.dependsOn(downstreamPureSourcesMain, downstreamPureSourcesTest, downstreamPureJSMain, upstreamPureJSMain, upstreamPureSourcesMain)
      downstreamPureJVM.dependsOn(downstreamPureJVMMain, downstreamPureJVMTest)
      downstreamPureJVMMain.dependsOn(downstreamPureSourcesMain, upstreamPureJVMMain, upstreamPureSourcesMain)
      downstreamPureJVMTest.dependsOn(downstreamPureSourcesMain, downstreamPureSourcesTest, downstreamPureJVMMain, upstreamPureJVMMain, upstreamPureSourcesMain)

      val upstreamFullGroup = Array(projectName, "upstreamFull")
      val upstreamFullSources = new myModule("upstreamFull-sources", upstreamFullGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/upstreamFull/shared")
        excluded := Seq("target")
      }
      val upstreamFullSourcesMain = new myModule("upstreamFull-sources.main", upstreamFullGroup) {
        standardRootsForSharedFullModule(this, "upstreamFull", "main", "2.13")
      }
      val upstreamFullSourcesTest = new myModule("upstreamFull-sources.test", upstreamFullGroup) {
        standardRootsForSharedFullModule(this, "upstreamFull", "test", "2.13")
      }
      val upstreamFullJS = new myModule("upstreamFullJS", upstreamFullGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/upstreamFull/js")
        excluded := Seq("target")
      }
      val upstreamFullJSMain = new myModule("upstreamFullJS.main", upstreamFullGroup) {
        standardRootsForFullCrossModule(this, "upstreamFull", "main", "2.13", "js")
      }
      val upstreamFullJSTest = new myModule("upstreamFullJS.test", upstreamFullGroup) {
        standardRootsForFullCrossModule(this, "upstreamFull", "test", "2.13", "js")
      }
      val upstreamFullJVM = new myModule("upstreamFullJVM", upstreamFullGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/upstreamFull/jvm")
        excluded := Seq("target")
      }
      val upstreamFullJVMMain = new myModule("upstreamFullJVM.main", upstreamFullGroup) {
        standardRootsForFullCrossModule(this, "upstreamFull", "main", "2.13", "jvm")
      }
      val upstreamFullJVMTest = new myModule("upstreamFullJVM.test", upstreamFullGroup) {
        standardRootsForFullCrossModule(this, "upstreamFull", "test", "2.13", "jvm")
      }
      upstreamFullSources.dependsOn(upstreamFullSourcesMain, upstreamFullSourcesTest)
      upstreamFullSourcesMain.dependsOn()
      upstreamFullSourcesTest.dependsOn(upstreamFullJVMMain)
      upstreamFullJS.dependsOn(upstreamFullJSMain, upstreamFullJSTest)
      upstreamFullJSMain.dependsOn(upstreamFullSourcesMain)
      upstreamFullJSTest.dependsOn(upstreamFullSourcesMain, upstreamFullSourcesTest, upstreamFullJSMain)
      upstreamFullJVM.dependsOn(upstreamFullJVMMain, upstreamFullJVMTest)
      upstreamFullJVMMain.dependsOn(upstreamFullSourcesMain)
      upstreamFullJVMTest.dependsOn(upstreamFullSourcesMain, upstreamFullSourcesTest, upstreamFullJVMMain)

      val downstreamFullGroup = Array(projectName, "downstreamFull")
      val downstreamFullSources = new myModule("downstreamFull-sources", downstreamFullGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/downstreamFull/shared")
        excluded := Seq("target")
      }
      val downstreamFullSourcesMain = new myModule("downstreamFull-sources.main", downstreamFullGroup) {
        standardRootsForSharedFullModule(this, "downstreamFull", "main", "2.13")
      }
      val downstreamFullSourcesTest = new myModule("downstreamFull-sources.test", downstreamFullGroup) {
        standardRootsForSharedFullModule(this, "downstreamFull", "test", "2.13")
      }
      val downstreamFullJS = new myModule("downstreamFullJS", downstreamFullGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/downstreamFull/js")
        excluded := Seq("target")
      }
      val downstreamFullJSMain = new myModule("downstreamFullJS.main", downstreamFullGroup) {
        standardRootsForFullCrossModule(this, "downstreamFull", "main", "2.13", "js")
      }
      val downstreamFullJSTest = new myModule("downstreamFullJS.test", downstreamFullGroup) {
        standardRootsForFullCrossModule(this, "downstreamFull", "test", "2.13", "js")
      }
      val downstreamFullJVM = new myModule("downstreamFullJVM", downstreamFullGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/downstreamFull/jvm")
        excluded := Seq("target")
      }
      val downstreamFullJVMMain = new myModule("downstreamFullJVM.main", downstreamFullGroup) {
        standardRootsForFullCrossModule(this, "downstreamFull", "main", "2.13", "jvm")
      }
      val downstreamFullJVMTest = new myModule("downstreamFullJVM.test", downstreamFullGroup) {
        standardRootsForFullCrossModule(this, "downstreamFull", "test", "2.13", "jvm")
      }
      downstreamFullSources.dependsOn(downstreamFullSourcesMain, downstreamFullSourcesTest)
      downstreamFullSourcesMain.dependsOn(upstreamFullJVMMain)
      downstreamFullSourcesTest.dependsOn(downstreamFullJVMMain, upstreamFullJVMMain)
      downstreamFullJS.dependsOn(downstreamFullJSMain, downstreamFullJSTest)
      downstreamFullJSMain.dependsOn(downstreamFullSourcesMain, upstreamFullJSMain, upstreamFullSourcesMain)
      downstreamFullJSTest.dependsOn(downstreamFullJSMain, downstreamFullSourcesMain, downstreamFullSourcesTest, upstreamFullJSMain, upstreamFullSourcesMain)
      downstreamFullJVM.dependsOn(downstreamFullJVMMain, downstreamFullJVMTest)
      downstreamFullJVMMain.dependsOn(downstreamFullSourcesMain, upstreamFullJVMMain, upstreamFullSourcesMain)
      downstreamFullJVMTest.dependsOn(downstreamFullJVMMain, downstreamFullSourcesMain, downstreamFullSourcesTest, upstreamFullJVMMain, upstreamFullSourcesMain)

      val downstreamPure: Seq[myModule] = Seq(
        downstreamPureSources, downstreamPureSourcesMain, downstreamPureSourcesTest,
        downstreamPureJS, downstreamPureJSMain, downstreamPureJSTest,
        downstreamPureJVM, downstreamPureJVMMain, downstreamPureJVMTest
      )
      val upstreamPure: Seq[myModule] = Seq(
        upstreamPureSources, upstreamPureSourcesMain, upstreamPureSourcesTest,
        upstreamPureJS, upstreamPureJSMain, upstreamPureJSTest,
        upstreamPureJVM, upstreamPureJVMMain, upstreamPureJVMTest
      )
      val downstreamFull: Seq[myModule] = Seq(
        downstreamFullSources, downstreamFullSourcesMain, downstreamFullSourcesTest,
        downstreamFullJS, downstreamFullJSMain, downstreamFullJSTest,
        downstreamFullJVM, downstreamFullJVMMain, downstreamFullJVMTest
      )
      val upstreamFull: Seq[myModule] = Seq(
        upstreamFullSources, upstreamFullSourcesMain, upstreamFullSourcesTest,
        upstreamFullJS, upstreamFullJSMain, upstreamFullJSTest,
        upstreamFullJVM, upstreamFullJVMMain, upstreamFullJVMTest
      )

      modules := Seq(rootModule, rootModuleMain, rootModuleTest, rootBuild) ++
        downstreamPure ++
        upstreamPure ++
        downstreamFull ++
        upstreamFull
    }

    val matcher = new ProjectStructureMatcher {
      override protected def defaultAssertMatch: ProjectStructureMatcher.AttributeMatchType =
        ProjectStructureMatcher.AttributeMatchType.Exact

      override protected def useNewLogicForSourceFolderComparison: Boolean = true
    }
    val compareContext = ProjectStructureComparisonContext.Implicit.default(using getProject)
      .withOptions(_.copy(strictCheckForBuildModules = true))
    matcher.assertProjectsEqual(expectedProject, getProject, singleContentRootModules = false)(using compareContext)
  }
}
