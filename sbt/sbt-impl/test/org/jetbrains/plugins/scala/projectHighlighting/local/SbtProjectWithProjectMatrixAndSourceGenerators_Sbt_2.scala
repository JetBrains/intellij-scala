package org.jetbrains.plugins.scala.projectHighlighting.local

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.projectHighlighting.base.SbtProjectHighlightingLocalProjectsTestBase
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.project.ProjectStructureDsl.{contentRoots, module}
import org.jetbrains.sbt.project.utils.ProjectStructureComparisonContext
import org.jetbrains.sbt.project.utils.ProjectStructureComparisonContext.AssertionFailStrategy.CollectErrors
import org.jetbrains.sbt.project.{CollectingNotificationsListener, ExactMatch, ProjectStructureDsl, ProjectStructureMatcher}

/**
 * @todo Extend this test to build across multiple platforms once the Scala.js and/or Scala Native plugins support sbt 2.
 *       See [[https://github.com/sbt/sbt/wiki/sbt-2.x-plugin-migration]]
 */
class SbtProjectWithProjectMatrixAndSourceGenerators_Sbt_2
  extends SbtProjectHighlightingLocalProjectsTestBase
    with ProjectStructureMatcher
    with ExactMatch {

  override def projectName = "sbt-projectmatrix-with-source-generators-sbt2"

  override protected def importProjectDuringTestSetup: Boolean = false

  override protected val projectFileName = projectName

  override protected def enableSeparateModulesForProdTest = true

  override protected def copyTestProjectToTemporaryDir = true

  override def setUp(): Unit = {
    super.setUp()
    injectVariable(
      getTestProjectPath / "project" / "build.properties",
      "$LATEST_SBT_2$",
      SbtVersion.Latest.Sbt_2.minor
    )
  }

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

  private def standardRoots(m: module, scope: String, scalaVersionMajor: String, minorSuffix: String): Unit = {
    import m._
    contentRoots := Seq(
      s"%PROJECT_ROOT%/src/$scope",
      s"%PROJECT_ROOT%/target/out/jvm/scala-$scalaVersionMajor.$minorSuffix/$projectName/src_managed/$scope",
      s"%PROJECT_ROOT%/target/out/jvm/scala-$scalaVersionMajor.$minorSuffix/$projectName/resource_managed/$scope"
    )

    val sources = Seq(
      s"%PROJECT_ROOT%/src/$scope/java",
      s"%PROJECT_ROOT%/src/$scope/scala",
      s"%PROJECT_ROOT%/src/$scope/scala-2",
      s"%PROJECT_ROOT%/src/$scope/scala-$scalaVersionMajor",
      s"%PROJECT_ROOT%/target/out/jvm/scala-$scalaVersionMajor.$minorSuffix/$projectName/src_managed/$scope",
    )
    if (scope == "test")
      ProjectStructureDsl.testSources := sources
    else
      ProjectStructureDsl.sources := sources
  }

  private def standardRootsForMatrixModule(m: module, moduleBaseName: String, scope: String, scalaVersionMajor: String, minorSuffix: String): Unit = {
    import m._
    contentRoots := Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala-$scalaVersionMajor",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scalajvm-$scalaVersionMajor",
      s"%PROJECT_ROOT%/target/out/jvm/scala-$scalaVersionMajor.$minorSuffix/$moduleBaseName/src_managed/$scope",
      s"%PROJECT_ROOT%/target/out/jvm/scala-$scalaVersionMajor.$minorSuffix/$moduleBaseName/resource_managed/$scope",
    )
    val sources = Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala-$scalaVersionMajor",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scalajvm-$scalaVersionMajor",
      s"%PROJECT_ROOT%/target/out/jvm/scala-$scalaVersionMajor.$minorSuffix/$moduleBaseName/src_managed/$scope"
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
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/javajvm",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala-2",
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
    excludeTargetDir: Boolean = false
  ) extends module(name, group) {
    import ProjectStructureDsl._
    locally {
      contentRoots := Seq()
      sources := Seq()
      testSources := Seq()
      moduleDependencies := Seq()
      val excludedDirs =
        if (excludeTargetDir) Seq("target")
        else Nil
      excluded := excludedDirs
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
      val sbtProjectmatrix= new myModule(projectName, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%")
      }
      val sbtProjectmatrixMain = new myModule(s"$projectName.main") {
        standardRoots(this, "main", "2.12", "17")
      }
      val sbtProjectmatrixTest = new myModule(s"$projectName.test") {
        standardRoots(this, "test", "2.12", "17")
      }
      sbtProjectmatrix.dependsOn(sbtProjectmatrixMain, sbtProjectmatrixTest)
      sbtProjectmatrixTest.dependsOn(sbtProjectmatrixMain)

      val sbtProjectmatrixBuild = new myModule(s"$projectName.$projectName-build") {
        contentRoots := Seq("%PROJECT_ROOT%/project")
        sources := Seq("%PROJECT_ROOT%/project")
        excluded := Seq("target", "project/target")
      }

      val upstreamGroup = Array(projectName, "upstream")
      val upstream2_11 = new myModule("upstream2_11", upstreamGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/upstream2_11")
      }
      val upstream2_11Main = new myModule("upstream2_11.main", upstreamGroup) {
        standardRootsForMatrixModule(this, "upstream", "main", "2.11", "12")
      }
      val upstream2_11Test = new myModule("upstream2_11.test", upstreamGroup) {
        standardRootsForMatrixModule(this, "upstream", "test", "2.11", "12")
      }
      val upstream2_12 = new myModule("upstream2_12", upstreamGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/upstream2_12")
      }
      val upstream2_12Main = new myModule("upstream2_12.main", upstreamGroup) {
        standardRootsForMatrixModule(this, "upstream", "main", "2.12", "17")
      }
      val upstream2_12Test = new myModule("upstream2_12.test", upstreamGroup) {
        standardRootsForMatrixModule(this, "upstream", "test", "2.12", "17")
      }
      val upstream2_13 = new myModule("upstream2_13", upstreamGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/upstream2_13")
      }
      val upstream2_13Main = new myModule("upstream2_13.main", upstreamGroup) {
        standardRootsForMatrixModule(this, "upstream", "main", "2.13", "10")
      }
      val upstream2_13Test = new myModule("upstream2_13.test", upstreamGroup) {
        standardRootsForMatrixModule(this, "upstream", "test", "2.13", "10")
      }
      val upstreamSources = new myModule("upstream-sources", upstreamGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/upstream")
      }
      val upstreamSourcesMain = new myModule("upstream-sources.main", upstreamGroup) {
        standardRootsForSharedModule(this, "upstream", "main")
      }
      val upstreamSourcesTest = new myModule("upstream-sources.test", upstreamGroup) {
        standardRootsForSharedModule(this, "upstream", "test")
      }

      upstreamSources.dependsOn(upstreamSourcesTest, upstreamSourcesMain)
      upstreamSourcesTest.dependsOn(upstream2_11Main)
      upstream2_11.dependsOn(upstream2_11Main, upstream2_11Test)
      upstream2_11Main.dependsOn(upstreamSourcesMain)
      upstream2_11Test.dependsOn(upstreamSourcesMain, upstreamSourcesTest, upstream2_11Main)
      upstream2_12.dependsOn(upstream2_12Test, upstream2_12Main)
      upstream2_12Main.dependsOn(upstreamSourcesMain)
      upstream2_12Test.dependsOn(upstreamSourcesMain, upstreamSourcesTest, upstream2_12Main)
      upstream2_13.dependsOn(upstream2_13Test, upstream2_13Main)
      upstream2_13Main.dependsOn(upstreamSourcesMain)
      upstream2_13Test.dependsOn(upstreamSourcesMain, upstreamSourcesTest, upstream2_13Main)

      val downstreamGroup = Array(projectName, "downstream")
      val downstream2_11 = new myModule("downstream2_11", downstreamGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/downstream2_11")
      }
      val downstream2_11Main = new myModule("downstream2_11.main", downstreamGroup) {
        standardRootsForMatrixModule(this, "downstream", "main", "2.11", "12")
      }
      val downstream2_11Test = new myModule("downstream2_11.test", downstreamGroup) {
        standardRootsForMatrixModule(this, "downstream", "test", "2.11", "12")
      }
      val downstream2_12 = new myModule("downstream2_12", downstreamGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/downstream2_12")
      }
      val downstream2_12Main = new myModule("downstream2_12.main", downstreamGroup) {
        standardRootsForMatrixModule(this, "downstream", "main", "2.12", "17")
      }
      val downstream2_12Test = new myModule("downstream2_12.test", downstreamGroup) {
        standardRootsForMatrixModule(this, "downstream", "test", "2.12", "17")
      }
      val downstream2_13 = new myModule("downstream2_13", downstreamGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/downstream2_13")
      }
      val downstream2_13Main = new myModule("downstream2_13.main", downstreamGroup) {
        standardRootsForMatrixModule(this, "downstream", "main", "2.13", "10")
      }
      val downstream2_13Test = new myModule("downstream2_13.test", downstreamGroup) {
        standardRootsForMatrixModule(this, "downstream", "test", "2.13", "10")
      }
      val downstreamSources = new myModule("downstream-sources", downstreamGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/downstream")
      }
      val downstreamSourcesMain = new myModule("downstream-sources.main", downstreamGroup) {
        standardRootsForSharedModule(this, "downstream", "main")
      }
      val downstreamSourcesTest = new myModule("downstream-sources.test", downstreamGroup) {
        standardRootsForSharedModule(this, "downstream", "test")
      }
      downstreamSources.dependsOn(downstreamSourcesMain, downstreamSourcesTest)
      downstreamSourcesMain.dependsOn(upstream2_11Main)
      downstreamSourcesTest.dependsOn(downstream2_11Main, upstream2_11Main)
      downstream2_11.dependsOn(downstream2_11Main, downstream2_11Test)
      downstream2_11Main.dependsOn(downstreamSourcesMain, upstreamSourcesMain, upstream2_11Main)
      downstream2_11Test.dependsOn(downstreamSourcesMain, downstreamSourcesTest, downstream2_11Main, upstreamSourcesMain, upstream2_11Main)
      downstream2_12.dependsOn(downstream2_12Main, downstream2_12Test)
      downstream2_12Main.dependsOn(downstreamSourcesMain, upstreamSourcesMain, upstream2_12Main)
      downstream2_12Test.dependsOn(downstreamSourcesMain, downstreamSourcesTest, downstream2_12Main, upstreamSourcesMain, upstream2_12Main)
      downstream2_13.dependsOn(downstream2_13Main, downstream2_13Test)
      downstream2_13Main.dependsOn(downstreamSourcesMain, upstreamSourcesMain, upstream2_13Main)
      downstream2_13Test.dependsOn(downstreamSourcesMain, downstreamSourcesTest, downstream2_13Main, upstreamSourcesMain, upstream2_13Main)


      val downstreamModules: Seq[myModule] = Seq(
        downstreamSources, downstreamSourcesMain, downstreamSourcesTest,
        downstream2_11, downstream2_11Main, downstream2_11Test,
        downstream2_12, downstream2_12Main, downstream2_12Test,
        downstream2_13, downstream2_13Main, downstream2_13Test,
      )
      val upstreamModules: Seq[myModule] = Seq(
        upstreamSources, upstreamSourcesMain, upstreamSourcesTest,
        upstream2_11, upstream2_11Main, upstream2_11Test,
        upstream2_12, upstream2_12Main, upstream2_12Test,
        upstream2_13, upstream2_13Main, upstream2_13Test
      )

      modules := Seq(sbtProjectmatrix, sbtProjectmatrixMain, sbtProjectmatrixTest, sbtProjectmatrixBuild) ++
          downstreamModules ++ upstreamModules
    }

    val matcher = new ProjectStructureMatcher {
      override protected def defaultAssertMatch: ProjectStructureMatcher.AttributeMatchType =
        ProjectStructureMatcher.AttributeMatchType.Exact

      override protected def useNewLogicForSourceFolderComparison: Boolean = true
    }
    val compareContext = ProjectStructureComparisonContext.Implicit.default(getProject)
      .withOptions(_.copy(strictCheckForBuildModules = true))
      .copy(assertionFailStrategy = new CollectErrors())

    matcher.assertProjectsEqual(expectedProject, getProject, singleContentRootModules = false)(compareContext)
    matcher.assertNoNotificationsShown(myProject, notificationsCollector.getNotifications)
  }
}
