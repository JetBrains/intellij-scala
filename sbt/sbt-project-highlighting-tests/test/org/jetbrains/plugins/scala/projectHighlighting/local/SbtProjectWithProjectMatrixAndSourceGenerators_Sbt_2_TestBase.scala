package org.jetbrains.plugins.scala.projectHighlighting.local

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.projectHighlighting.base.SbtProjectHighlightingLocalProjectsTestBase
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.project.ProjectStructureDsl.{contentRoots, module, project}
import org.jetbrains.sbt.project.utils.ProjectStructureComparisonContext
import org.jetbrains.sbt.project.utils.ProjectStructureComparisonContext.AssertionFailStrategy.CollectErrors
import org.jetbrains.plugins.scala.notifications.CollectingNotificationsListener
import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase.TestProjectCopyOptions
import org.jetbrains.sbt.project.{ExactMatch, ProjectStructureDsl, ProjectStructureMatcher}

abstract class SbtProjectWithProjectMatrixAndSourceGenerators_Sbt_2_TestBase
  extends SbtProjectHighlightingLocalProjectsTestBase
    with ProjectStructureMatcher
    with ExactMatch {

  override protected def importProjectDuringTestSetup: Boolean = false

  override protected val projectFileName = projectName

  override protected def getTestProjectCopyOptions: TestProjectCopyOptions =
    super.getTestProjectCopyOptions.copy(copyToTemporaryDir = true)

  override protected def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_17

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

  protected final def standardRoots(m: module, scope: String, scalaVersionMajor: String, minorSuffix: String): Unit = {
    import m.*
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
    if (scope == "test") ProjectStructureDsl.testSources := sources
    else ProjectStructureDsl.sources := sources
  }

  protected final def standardRootsForMatrixModulePlatform(
    m: module,
    moduleBaseName: String,
    scope: String,
    scalaVersionMajor: String,
    minorSuffix: String,
    platform: String,
    includeLegacyScalaVersionRoot: Boolean = false,
  ): Unit = {
    import m.*
    val managedSourcesModuleBaseName = moduleBaseName.toLowerCase
    val managedSourcesPlatform = if (platform == "native") "native0.5" else platform
    val legacyScalaVersionRoots =
      if (includeLegacyScalaVersionRoot) Seq(s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala-$scalaVersionMajor")
      else Seq.empty
    contentRoots := legacyScalaVersionRoots ++ Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala$platform-$scalaVersionMajor",
      s"%PROJECT_ROOT%/target/out/$managedSourcesPlatform/scala-$scalaVersionMajor.$minorSuffix/$managedSourcesModuleBaseName/src_managed/$scope",
      s"%PROJECT_ROOT%/target/out/$managedSourcesPlatform/scala-$scalaVersionMajor.$minorSuffix/$managedSourcesModuleBaseName/resource_managed/$scope",
    )
    val sources = legacyScalaVersionRoots ++ Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala$platform-$scalaVersionMajor",
      s"%PROJECT_ROOT%/target/out/$managedSourcesPlatform/scala-$scalaVersionMajor.$minorSuffix/$managedSourcesModuleBaseName/src_managed/$scope",
    )

    if (scope == "test") ProjectStructureDsl.testSources := sources
    else ProjectStructureDsl.sources := sources
  }

  protected final def standardRootsForSharedModule(m: module, moduleBaseName: String, scope: String): Unit = {
    import m.*
    contentRoots := Seq(s"%PROJECT_ROOT%/$moduleBaseName/src/$scope")
    val sources = Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/java",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/javajvm",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala-2",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scalajvm",
    )

    if (scope == "test") ProjectStructureDsl.testSources := sources
    else ProjectStructureDsl.sources := sources
  }

  protected final def standardRootsForSharedMultiPlatformModule(m: module, moduleBaseName: String, scope: String): Unit = {
    import m.*
    contentRoots := Seq(s"%PROJECT_ROOT%/$moduleBaseName/src/$scope")
    val sources = Seq(
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/java",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/javajvm",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/javanative",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala-2",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala-2.12",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scala-2.13",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scalajvm",
      s"%PROJECT_ROOT%/$moduleBaseName/src/$scope/scalanative",
    )

    if (scope == "test") ProjectStructureDsl.testSources := sources
    else ProjectStructureDsl.sources := sources
  }

  protected class myModule(
    name: String,
    group: Array[String] = Array.empty,
    excludeTargetDir: Boolean = false
  ) extends module(name, group) {

    import ProjectStructureDsl.*

    locally {
      contentRoots := Seq()
      sources := Seq()
      testSources := Seq()
      moduleDependencies := Seq()
      val excludedDirs = if (excludeTargetDir) Seq("target") else Nil
      excluded := excludedDirs
    }
  }

  protected final def assertProjectStructure(expectedProject: project, notificationsCollector: CollectingNotificationsListener): Unit = {
    val matcher = new ProjectStructureMatcher {
      override protected def defaultAssertMatch: ProjectStructureMatcher.AttributeMatchType =
        ProjectStructureMatcher.AttributeMatchType.Exact

      override protected def useNewLogicForSourceFolderComparison: Boolean = true
    }
    val compareContext = ProjectStructureComparisonContext.Implicit.default(using getProject)
      .withOptions(_.copy(strictCheckForBuildModules = true))
      .copy(assertionFailStrategy = new CollectErrors())

    matcher.assertProjectsEqual(expectedProject, getProject, singleContentRootModules = false)(using compareContext)
    matcher.assertNoNotificationsShown(getProject, notificationsCollector.getNotifications)
  }
}
