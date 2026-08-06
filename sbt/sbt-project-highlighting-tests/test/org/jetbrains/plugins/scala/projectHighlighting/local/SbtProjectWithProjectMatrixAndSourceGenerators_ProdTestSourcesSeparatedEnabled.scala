package org.jetbrains.plugins.scala.projectHighlighting.local

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.projectHighlighting.base.SbtProjectHighlightingLocalProjectsTestBase
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.sbt.project.ProjectStructureDsl.{contentRoots, module}
import org.jetbrains.sbt.project.utils.ProjectStructureComparisonContext
import org.jetbrains.sbt.project.utils.ProjectStructureComparisonContext.AssertionFailStrategy.CollectErrors
import org.jetbrains.plugins.scala.notifications.CollectingNotificationsListener
import org.jetbrains.sbt.project.{ExactMatch, ProjectStructureDsl, ProjectStructureMatcher}

class SbtProjectWithProjectMatrixAndSourceGenerators_ProdTestSourcesSeparatedEnabled
  extends SbtProjectHighlightingLocalProjectsTestBase
    with ProjectStructureMatcher
    with ExactMatch {

  override def projectName = "sbt-projectmatrix-with-source-generators"

  override protected def importProjectDuringTestSetup: Boolean = false

  override protected val projectFileName = projectName

  override def testHighlighting(): Unit = {
    importProject(false)
    super.testHighlighting()
  }

  override protected def highlightSingleFile(
    virtualFile: VirtualFile,
    psiFile: PsiFile,
    reporter: HighlightingProgressReporter,
  ): Unit =
    doHighlightingForFile(virtualFile, psiFile, reporter)

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

  private def standardRootsForMatrixModule(m: module, moduleBaseName: String, scope: String, scalaVersionMajor: String): Unit = {
    import m._
    contentRoots := Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala-$scalaVersionMajor",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scalajvm-$scalaVersionMajor",
      s"%PROJECT_ROOT%/$moduleBaseName/target/jvm-$scalaVersionMajor/src_managed/$scope",
      s"%PROJECT_ROOT%/$moduleBaseName/target/jvm-$scalaVersionMajor/resource_managed/$scope",
    )
    val sources = Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala-$scalaVersionMajor",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scalajvm-$scalaVersionMajor",
      s"%PROJECT_ROOT%/$moduleBaseName/target/jvm-$scalaVersionMajor/src_managed/$scope"
    )

    if (scope == "test")
      ProjectStructureDsl.testSources := sources
    else
      ProjectStructureDsl.sources := sources
  }

  private def standardRootsForMatrixModuleBothPlatforms(m: module, moduleBaseName: String, scope: String, scalaVersionMajor: String, platform: String): Unit = {
    import m._
    contentRoots := Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala$platform-$scalaVersionMajor",
      s"%PROJECT_ROOT%/$moduleBaseName/target/$platform-$scalaVersionMajor/src_managed/$scope",
      s"%PROJECT_ROOT%/$moduleBaseName/target/$platform-$scalaVersionMajor/resource_managed/$scope",
    )
    val sources = Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala$platform-$scalaVersionMajor",
      s"%PROJECT_ROOT%/$moduleBaseName/target/$platform-$scalaVersionMajor/src_managed/$scope",
    )

    if (scope == "test")
      ProjectStructureDsl.testSources := sources
    else
      ProjectStructureDsl.sources := sources
  }

  private def standardRootsForSharedModule(m: module, moduleBaseName: String, scope: String): Unit = {
    import m._
    contentRoots := Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope"
    )
    val sources = Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/java",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala-2",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scalajvm",
    )

    if (scope == "test")
      ProjectStructureDsl.testSources := sources
    else
      ProjectStructureDsl.sources := sources
  }

  private def standardRootsForSharedModuleBothPlatforms(m: module, moduleBaseName: String, scope: String): Unit = {
    import m._
    contentRoots := Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope"
    )
    val sources = Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/java",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala-2",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala-2.11",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala-2.12",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala-2.13",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scalajs",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scalajvm",
    )

    if (scope == "test")
      ProjectStructureDsl.testSources := sources
    else
      ProjectStructureDsl.sources := sources
  }

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


  //noinspection ScalaUnusedSymbol,TypeAnnotation
  def testProjectStructure(): Unit = {
    val notificationsCollector = CollectingNotificationsListener.subscribeOnWarningsAndErrors(getProject)

    importProject(false)

    import org.jetbrains.sbt.project.ProjectStructureDsl._

    val expectedProject: project = new project(projectName) {
      val sbtProjectmatrix= new myModule(projectName) {
        contentRoots := Seq("%PROJECT_ROOT%")
        excluded := Seq("target")
      }
      val sbtProjectmatrixMain = new myModule(s"$projectName.main") {
        standardRoots(this, "main", "2.12")
      }
      val sbtProjectmatrixTest = new myModule(s"$projectName.test") {
        standardRoots(this, "test", "2.12")
      }
      sbtProjectmatrix.dependsOn(sbtProjectmatrixMain, sbtProjectmatrixTest)
      sbtProjectmatrixTest.dependsOn(sbtProjectmatrixMain)

      val sbtProjectmatrixBuild = new myModule(s"$projectName.sbt-projectmatrix-with-source-generators-build") {
        contentRoots := Seq("%PROJECT_ROOT%/project")
        sources := Seq("%PROJECT_ROOT%/project")
        excluded := Seq("target", "project/target")
      }

      val upstreamGroup = Array(projectName, "upstream")
      val upstream = new myModule("upstream", upstreamGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/upstream")
      }
      val upstreamMain = new myModule("upstream.main", upstreamGroup) {
        standardRootsForMatrixModule(this, "upstream", "main", "2.13")
      }
      val upstreamTest = new myModule("upstream.test", upstreamGroup) {
        standardRootsForMatrixModule(this, "upstream", "test", "2.13")
      }
      val upstream2_11 = new myModule("upstream2_11", upstreamGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/upstream2_11")
      }
      val upstream2_11Main = new myModule("upstream2_11.main", upstreamGroup) {
        standardRootsForMatrixModule(this, "upstream", "main", "2.11")
      }
      val upstream2_11Test = new myModule("upstream2_11.test", upstreamGroup) {
        standardRootsForMatrixModule(this, "upstream", "test", "2.11")
      }
      val upstream2_12 = new myModule("upstream2_12", upstreamGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/upstream2_12")
      }
      val upstream2_12Main = new myModule("upstream2_12.main", upstreamGroup) {
        standardRootsForMatrixModule(this, "upstream", "main", "2.12")
      }
      val upstream2_12Test = new myModule("upstream2_12.test", upstreamGroup) {
        standardRootsForMatrixModule(this, "upstream", "test", "2.12")
      }
      val upstreamSources = new myModule("upstream-sources", upstreamGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/upstream")
        excluded := Seq("target")
      }
      val upstreamSourcesMain = new myModule("upstream-sources.main", upstreamGroup) {
        standardRootsForSharedModule(this, "upstream", "main")
      }
      val upstreamSourcesTest = new myModule("upstream-sources.test", upstreamGroup) {
        standardRootsForSharedModule(this, "upstream", "test")
      }

      upstream.dependsOn(upstreamMain, upstreamTest)
      upstreamMain.dependsOn(upstreamSourcesMain)
      upstreamTest.dependsOn(upstreamSourcesMain, upstreamSourcesTest, upstreamMain)
      upstreamSources.dependsOn(upstreamSourcesTest, upstreamSourcesMain)
      upstreamSourcesTest.dependsOn(upstreamMain)
      upstream2_11.dependsOn(upstream2_11Main, upstream2_11Test)
      upstream2_11Main.dependsOn(upstreamSourcesMain)
      upstream2_11Test.dependsOn(upstreamSourcesMain, upstreamSourcesTest, upstream2_11Main)
      upstream2_12.dependsOn(upstream2_12Test, upstream2_12Main)
      upstream2_12Main.dependsOn(upstreamSourcesMain)
      upstream2_12Test.dependsOn(upstreamSourcesMain, upstreamSourcesTest, upstream2_12Main)

      val downstreamGroup = Array(projectName, "downstream")
      val downstream = new myModule("downstream", downstreamGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/downstream")
      }

      val downstreamMain = new myModule("downstream.main", downstreamGroup) {
        standardRootsForMatrixModule(this, "downstream", "main", "2.13")
      }
      val downstreamTest = new myModule(s"downstream.test", downstreamGroup) {
        standardRootsForMatrixModule(this, "downstream", "test", "2.13")
      }
      val downstream2_11 = new myModule("downstream2_11", downstreamGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/downstream2_11")
      }
      val downstream2_11Main = new myModule("downstream2_11.main", downstreamGroup) {
        standardRootsForMatrixModule(this, "downstream", "main", "2.11")
      }
      val downstream2_11Test = new myModule("downstream2_11.test", downstreamGroup) {
        standardRootsForMatrixModule(this, "downstream", "test", "2.11")
      }
      val downstream2_12 = new myModule("downstream2_12", downstreamGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/downstream2_12")
      }
      val downstream2_12Main = new myModule("downstream2_12.main", downstreamGroup) {
        standardRootsForMatrixModule(this, "downstream", "main", "2.12")
      }
      val downstream2_12Test = new myModule("downstream2_12.test", downstreamGroup) {
        standardRootsForMatrixModule(this, "downstream", "test", "2.12")
      }
      val downstreamSources = new myModule("downstream-sources", downstreamGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/downstream")
        excluded := Seq("target")
      }
      val downstreamSourcesMain = new myModule("downstream-sources.main", downstreamGroup) {
        standardRootsForSharedModule(this, "downstream", "main")
      }
      val downstreamSourcesTest = new myModule("downstream-sources.test", downstreamGroup) {
        standardRootsForSharedModule(this, "downstream", "test")
      }
      downstream.dependsOn(downstreamMain, downstreamTest)
      downstreamMain.dependsOn(upstreamMain, upstreamSourcesMain, downstreamSourcesMain)
      downstreamTest.dependsOn(upstreamMain, downstreamSourcesTest, downstreamMain, upstreamSourcesMain, downstreamSourcesMain)
      downstreamSources.dependsOn(downstreamSourcesMain, downstreamSourcesTest)
      downstreamSourcesMain.dependsOn(upstreamMain)
      downstreamSourcesTest.dependsOn(downstreamMain, upstreamMain)
      downstream2_11.dependsOn(downstream2_11Main, downstream2_11Test)
      downstream2_11Main.dependsOn(downstreamSourcesMain, upstreamSourcesMain, upstream2_11Main)
      downstream2_11Test.dependsOn(downstreamSourcesMain, downstreamSourcesTest, downstream2_11Main, upstreamSourcesMain, upstream2_11Main)
      downstream2_12.dependsOn(downstream2_12Main, downstream2_12Test)
      downstream2_12Main.dependsOn(downstreamSourcesMain, upstreamSourcesMain, upstream2_12Main)
      downstream2_12Test.dependsOn(downstreamSourcesMain, downstreamSourcesTest, downstream2_12Main, upstreamSourcesMain, upstream2_12Main)

      val upstreamBothPlatformsGroup = Array(projectName, "upstreamBothPlatforms")
      val upstreamBothPlatforms = new myModule("upstreamBothPlatforms", upstreamBothPlatformsGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/upstreamBothPlatforms")
      }
      val upstreamBothPlatformsMain = new myModule("upstreamBothPlatforms.main", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "upstreamBothPlatforms", "main", "2.13", "jvm")
      }
      val upstreamBothPlatformsTest = new myModule("upstreamBothPlatforms.test", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "upstreamBothPlatforms", "test", "2.13", "jvm")
      }
      val upstreamBothPlatforms2_11 = new myModule("upstreamBothPlatforms2_11", upstreamBothPlatformsGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/upstreamBothPlatforms2_11")
      }
      val upstreamBothPlatforms2_11Main = new myModule("upstreamBothPlatforms2_11.main", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "upstreamBothPlatforms", "main", "2.11", "jvm")
      }
      val upstreamBothPlatforms2_11Test = new myModule("upstreamBothPlatforms2_11.test", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "upstreamBothPlatforms", "test", "2.11", "jvm")
      }
      val upstreamBothPlatforms2_12 = new myModule("upstreamBothPlatforms2_12", upstreamBothPlatformsGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/upstreamBothPlatforms2_12")
      }
      val upstreamBothPlatforms2_12Main = new myModule("upstreamBothPlatforms2_12.main", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "upstreamBothPlatforms", "main", "2.12", "jvm")
      }
      val upstreamBothPlatforms2_12Test = new myModule("upstreamBothPlatforms2_12.test", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "upstreamBothPlatforms", "test", "2.12", "jvm")
      }
      val upstreamBothPlatformsJS = new myModule("upstreamBothPlatformsJS", upstreamBothPlatformsGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/upstreamBothPlatformsJS")
      }
      val upstreamBothPlatformsJSMain = new myModule("upstreamBothPlatformsJS.main", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "upstreamBothPlatforms", "main", "2.13", "js")
      }
      val upstreamBothPlatformsJSTest = new myModule("upstreamBothPlatformsJS.test", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "upstreamBothPlatforms", "test", "2.13", "js")
      }
      val upstreamBothPlatformsJS2_11 = new myModule("upstreamBothPlatformsJS2_11", upstreamBothPlatformsGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/upstreamBothPlatformsJS2_11")
      }
      val upstreamBothPlatformsJS2_11Main = new myModule("upstreamBothPlatformsJS2_11.main", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "upstreamBothPlatforms", "main", "2.11", "js")
      }
      val upstreamBothPlatformsJS2_11Test = new myModule("upstreamBothPlatformsJS2_11.test", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "upstreamBothPlatforms", "test", "2.11", "js")
      }
      val upstreamBothPlatformsJS2_12 = new myModule("upstreamBothPlatformsJS2_12", upstreamBothPlatformsGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/upstreamBothPlatformsJS2_12")
      }
      val upstreamBothPlatformsJS2_12Main = new myModule("upstreamBothPlatformsJS2_12.main", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "upstreamBothPlatforms", "main", "2.12", "js")
      }
      val upstreamBothPlatformsJS2_12Test = new myModule("upstreamBothPlatformsJS2_12.test", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "upstreamBothPlatforms", "test", "2.12", "js")
      }
      val upstreamBothPlatformsSources = new myModule("upstreamBothPlatforms-sources", upstreamBothPlatformsGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/upstreamBothPlatforms")
        excluded := Seq("target")
      }
      val upstreamBothPlatformsSourcesMain = new myModule("upstreamBothPlatforms-sources.main", upstreamBothPlatformsGroup) {
        standardRootsForSharedModuleBothPlatforms(this, "upstreamBothPlatforms", "main")
      }
      val upstreamBothPlatformsSourcesTest = new myModule("upstreamBothPlatforms-sources.test", upstreamBothPlatformsGroup) {
        standardRootsForSharedModuleBothPlatforms(this, "upstreamBothPlatforms", "test")
      }

      upstreamBothPlatforms.dependsOn(upstreamBothPlatformsTest, upstreamBothPlatformsMain)
      upstreamBothPlatformsMain.dependsOn(upstreamBothPlatformsSourcesMain)
      upstreamBothPlatformsTest.dependsOn(upstreamBothPlatformsMain, upstreamBothPlatformsSourcesTest, upstreamBothPlatformsSourcesMain)
      upstreamBothPlatformsSources.dependsOn(upstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesTest)
      upstreamBothPlatformsSourcesTest.dependsOn(upstreamBothPlatformsMain)
      upstreamBothPlatforms2_11Main.dependsOn(upstreamBothPlatformsSourcesMain)
      upstreamBothPlatforms2_11Test.dependsOn(upstreamBothPlatforms2_11Main, upstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesTest)
      upstreamBothPlatforms2_12.dependsOn(upstreamBothPlatforms2_12Main, upstreamBothPlatforms2_12Test)
      upstreamBothPlatforms2_11.dependsOn(upstreamBothPlatforms2_11Main, upstreamBothPlatforms2_11Test)
      upstreamBothPlatforms2_12Main.dependsOn(upstreamBothPlatformsSourcesMain)
      upstreamBothPlatforms2_12Test.dependsOn(upstreamBothPlatforms2_12Main, upstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesTest)
      upstreamBothPlatformsJS.dependsOn(upstreamBothPlatformsJSMain, upstreamBothPlatformsJSTest)
      upstreamBothPlatformsJSMain.dependsOn(upstreamBothPlatformsSourcesMain)
      upstreamBothPlatformsJSTest.dependsOn(upstreamBothPlatformsJSMain, upstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesTest)
      upstreamBothPlatformsJS2_11.dependsOn(upstreamBothPlatformsJS2_11Main, upstreamBothPlatformsJS2_11Test)
      upstreamBothPlatformsJS2_11Main.dependsOn(upstreamBothPlatformsSourcesMain)
      upstreamBothPlatformsJS2_11Test.dependsOn(upstreamBothPlatformsJS2_11Main, upstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesTest)
      upstreamBothPlatformsJS2_12.dependsOn(upstreamBothPlatformsJS2_12Main, upstreamBothPlatformsJS2_12Test)
      upstreamBothPlatformsJS2_12Main.dependsOn(upstreamBothPlatformsSourcesMain)
      upstreamBothPlatformsJS2_12Test.dependsOn(upstreamBothPlatformsJS2_12Main, upstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesTest)

      val downstreamBothPlatformsGroup = Array(projectName, "downstreamBothPlatforms")
      val downstreamBothPlatforms = new myModule("downstreamBothPlatforms", downstreamBothPlatformsGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/downstreamBothPlatforms")
      }
      val downstreamBothPlatformsMain = new myModule("downstreamBothPlatforms.main", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "downstreamBothPlatforms", "main", "2.13", "jvm")
      }
      val downstreamBothPlatformsTest = new myModule("downstreamBothPlatforms.test", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "downstreamBothPlatforms", "test", "2.13", "jvm")
      }
      val downstreamBothPlatforms2_12 = new myModule("downstreamBothPlatforms2_12", downstreamBothPlatformsGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/downstreamBothPlatforms2_12")
      }
      val downstreamBothPlatforms2_12Main = new myModule("downstreamBothPlatforms2_12.main", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "downstreamBothPlatforms", "main", "2.12", "jvm")
      }
      val downstreamBothPlatforms2_12Test = new myModule("downstreamBothPlatforms2_12.test", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "downstreamBothPlatforms", "test", "2.12", "jvm")
      }
      val downstreamBothPlatforms2_11 = new myModule("downstreamBothPlatforms2_11", downstreamBothPlatformsGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/downstreamBothPlatforms2_11")
      }
      val downstreamBothPlatforms2_11Main = new myModule("downstreamBothPlatforms2_11.main", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "downstreamBothPlatforms", "main", "2.11", "jvm")
      }
      val downstreamBothPlatforms2_11Test = new myModule("downstreamBothPlatforms2_11.test", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "downstreamBothPlatforms", "test", "2.11", "jvm")
      }
      val downstreamBothPlatformsJS = new myModule("downstreamBothPlatformsJS", downstreamBothPlatformsGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/downstreamBothPlatformsJS")
      }
      val downstreamBothPlatformsJSMain = new myModule("downstreamBothPlatformsJS.main", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "downstreamBothPlatforms", "main", "2.13", "js")
      }
      val downstreamBothPlatformsJSTest = new myModule("downstreamBothPlatformsJS.test", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "downstreamBothPlatforms", "test", "2.13", "js")
      }
      val downstreamBothPlatformsJS2_11 = new myModule("downstreamBothPlatformsJS2_11", downstreamBothPlatformsGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/downstreamBothPlatformsJS2_11")
      }
      val downstreamBothPlatformsJS2_11Main = new myModule("downstreamBothPlatformsJS2_11.main", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "downstreamBothPlatforms", "main", "2.11", "js")
      }
      val downstreamBothPlatformsJS2_11Test = new myModule("downstreamBothPlatformsJS2_11.test", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "downstreamBothPlatforms", "test", "2.11", "js")
      }
      val downstreamBothPlatformsJS2_12 = new myModule("downstreamBothPlatformsJS2_12", downstreamBothPlatformsGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/downstreamBothPlatformsJS2_12")
      }
      val downstreamBothPlatformsJS2_12Main = new myModule("downstreamBothPlatformsJS2_12.main", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "downstreamBothPlatforms", "main", "2.12", "js")
      }
      val downstreamBothPlatformsJS2_12Test = new myModule("downstreamBothPlatformsJS2_12.test", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModuleBothPlatforms(this, "downstreamBothPlatforms", "test", "2.12", "js")
      }
      val downstreamBothPlatformsSources = new myModule("downstreamBothPlatforms-sources", downstreamBothPlatformsGroup) {
        contentRoots := Seq("%PROJECT_ROOT%/downstreamBothPlatforms")
        excluded := Seq("target")
      }
      val downstreamBothPlatformsSourcesMain = new myModule("downstreamBothPlatforms-sources.main", downstreamBothPlatformsGroup) {
        standardRootsForSharedModuleBothPlatforms(this, "downstreamBothPlatforms", "main")
      }
      val downstreamBothPlatformsSourcesTest = new myModule("downstreamBothPlatforms-sources.test", downstreamBothPlatformsGroup) {
        standardRootsForSharedModuleBothPlatforms(this, "downstreamBothPlatforms", "test")
      }

      downstreamBothPlatforms.dependsOn(downstreamBothPlatformsMain, downstreamBothPlatformsTest)
      downstreamBothPlatformsMain.dependsOn(upstreamBothPlatformsMain, upstreamBothPlatformsSourcesMain, downstreamBothPlatformsSourcesMain)
      downstreamBothPlatformsTest.dependsOn(downstreamBothPlatformsMain, downstreamBothPlatformsSourcesTest, upstreamBothPlatformsMain, upstreamBothPlatformsSourcesMain, downstreamBothPlatformsSourcesMain)
      downstreamBothPlatformsSources.dependsOn(downstreamBothPlatformsSourcesMain, downstreamBothPlatformsSourcesTest)
      downstreamBothPlatformsSourcesMain.dependsOn(upstreamBothPlatformsMain)
      downstreamBothPlatformsSourcesTest.dependsOn(downstreamBothPlatformsMain, upstreamBothPlatformsMain)
      downstreamBothPlatforms2_12.dependsOn(downstreamBothPlatforms2_12Main, downstreamBothPlatforms2_12Test)
      downstreamBothPlatforms2_12Main.dependsOn(upstreamBothPlatforms2_12Main, downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain)
      downstreamBothPlatforms2_12Test.dependsOn(downstreamBothPlatforms2_12Main, upstreamBothPlatforms2_12Main, downstreamBothPlatformsSourcesTest, downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain)
      downstreamBothPlatforms2_11.dependsOn(downstreamBothPlatforms2_11Main, downstreamBothPlatforms2_11Test)
      downstreamBothPlatforms2_11Main.dependsOn(upstreamBothPlatforms2_11Main, downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain)
      downstreamBothPlatforms2_11Test.dependsOn(downstreamBothPlatforms2_11Main, upstreamBothPlatforms2_11Main, downstreamBothPlatformsSourcesTest, downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain)
      downstreamBothPlatformsJS.dependsOn(downstreamBothPlatformsJSMain, downstreamBothPlatformsJSTest)
      downstreamBothPlatformsJSMain.dependsOn(upstreamBothPlatformsJSMain, downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain)
      downstreamBothPlatformsJSTest.dependsOn(upstreamBothPlatformsJSMain, downstreamBothPlatformsJSMain, downstreamBothPlatformsSourcesTest, downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain)
      downstreamBothPlatformsJS2_11.dependsOn(downstreamBothPlatformsJS2_11Main, downstreamBothPlatformsJS2_11Test)
      downstreamBothPlatformsJS2_11Main.dependsOn(upstreamBothPlatformsJS2_11Main, downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain)
      downstreamBothPlatformsJS2_11Test.dependsOn(upstreamBothPlatformsJS2_11Main, downstreamBothPlatformsJS2_11Main, downstreamBothPlatformsSourcesTest, downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain)
      downstreamBothPlatformsJS2_12.dependsOn(downstreamBothPlatformsJS2_12Main, downstreamBothPlatformsJS2_12Test)
      downstreamBothPlatformsJS2_12Main.dependsOn(upstreamBothPlatformsJS2_12Main, downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain)
      downstreamBothPlatformsJS2_12Test.dependsOn(upstreamBothPlatformsJS2_12Main, downstreamBothPlatformsJS2_12Main, downstreamBothPlatformsSourcesTest, downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain)

      val downstreamModules: Seq[myModule] = Seq(
        downstreamSources, downstreamSourcesMain, downstreamSourcesTest,
        downstream, downstreamMain, downstreamTest,
        downstream2_11, downstream2_11Main, downstream2_11Test,
        downstream2_12, downstream2_12Main, downstream2_12Test
      )
      val upstreamModules: Seq[myModule] = Seq(
        upstreamSources, upstreamSourcesMain, upstreamSourcesTest,
        upstream, upstreamMain, upstreamTest,
        upstream2_11, upstream2_11Main, upstream2_11Test,
        upstream2_12, upstream2_12Main, upstream2_12Test
      )
      val downstreamBothPlatformsModules: Seq[myModule] = Seq(
        downstreamBothPlatformsSources, downstreamBothPlatformsSourcesMain, downstreamBothPlatformsSourcesTest,
        downstreamBothPlatforms, downstreamBothPlatformsMain, downstreamBothPlatformsTest,
        downstreamBothPlatforms2_11, downstreamBothPlatforms2_11Main, downstreamBothPlatforms2_11Test,
        downstreamBothPlatforms2_12, downstreamBothPlatforms2_12Main, downstreamBothPlatforms2_12Test,
        downstreamBothPlatformsJS, downstreamBothPlatformsJSMain, downstreamBothPlatformsJSTest,
        downstreamBothPlatformsJS2_11, downstreamBothPlatformsJS2_11Main, downstreamBothPlatformsJS2_11Test,
        downstreamBothPlatformsJS2_12, downstreamBothPlatformsJS2_12Main, downstreamBothPlatformsJS2_12Test
      )
      val upstreamBothPlatformsModules: Seq[myModule] = Seq(
        upstreamBothPlatforms, upstreamBothPlatformsMain, upstreamBothPlatformsTest,
        upstreamBothPlatformsSources, upstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesTest,
        upstreamBothPlatforms2_11, upstreamBothPlatforms2_11Main, upstreamBothPlatforms2_11Test,
        upstreamBothPlatforms2_12, upstreamBothPlatforms2_12Main, upstreamBothPlatforms2_12Test,
        upstreamBothPlatformsJS, upstreamBothPlatformsJSMain, upstreamBothPlatformsJSTest,
        upstreamBothPlatformsJS2_11, upstreamBothPlatformsJS2_11Main, upstreamBothPlatformsJS2_11Test,
        upstreamBothPlatformsJS2_12, upstreamBothPlatformsJS2_12Main, upstreamBothPlatformsJS2_12Test
      )

      modules :=
        Seq(sbtProjectmatrix, sbtProjectmatrixMain, sbtProjectmatrixTest, sbtProjectmatrixBuild) ++
          downstreamModules ++
          upstreamModules ++
          downstreamBothPlatformsModules ++
          upstreamBothPlatformsModules
    }

    val matcher = new ProjectStructureMatcher {
      override protected def defaultAssertMatch: ProjectStructureMatcher.AttributeMatchType =
        ProjectStructureMatcher.AttributeMatchType.Exact

      override protected def useNewLogicForSourceFolderComparison: Boolean = true
    }
    val compareContext = ProjectStructureComparisonContext.Implicit.default(using getProject)
      .withOptions(_.copy(strictCheckForBuildModules = true))
      .copy(assertionFailStrategy = new CollectErrors())

    matcher.assertProjectsEqual(expectedProject, getProject, singleContentRootModules = false)(using compareContext)

    matcher.assertNoNotificationsShown(getMyProject, notificationsCollector.getNotifications)
  }
}
