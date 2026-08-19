package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.compiler.testUtils.CompilerUtils
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase.TestProjectCopyOptions
import org.jetbrains.sbt.project.directoryCompletion.SbtDirectoryCompletionFixture
import org.jetbrains.sbt.project.utils.{JavaCompilerOptionsUtils, ProjectComparisonOptions, ProjectStructureComparisonContext}
import org.jetbrains.sbt.settings.SbtSettings
import org.junit.Assert
import org.junit.Assert.fail

import java.nio.file.Path
import scala.jdk.CollectionConverters.CollectionHasAsScala

/**
 * Base class for tests that verify the IDE project model produced by an sbt import.
 *
 * The sbt-specific external-system setup is provided by [[SbtExternalSystemImportingTestLike]].
 *
 * This class builds on that setup and adds the project-structure testing layer:
 *
 *  - [[ProjectStructureAssertionsFixture]] for comparing the expected IDE project layout
 *  - default sbt project test-data location under `testdata/sbt/projects`
 *  - preview import support via [[isPreview]]
 *  - [[runTest]] helpers that:
 *    - import the project
 *    - validate imported external project data
 *    - compare the IDE structure
 *    - and assert that no unexpected notifications were shown
 *  - helpers for common sbt roots, directory-completion variants, compiler options, and test builds
 *
 * Use this base when the main assertion is the imported project structure.
 *
 * Tests that only need to import an sbt project and exercise runtime/process behavior
 * should usually extend [[SbtExternalSystemImportingTestLike]] or a more focused runtime base.
 */
abstract class SbtProjectStructureImportingTestBase extends SbtExternalSystemImportingTestLike {

  import ProjectStructureDsl.*

  /**
   * When set to `true` during a test run, only a preview import is performed.
   */
  protected def isPreview: Boolean = false

  // Resolve the testdata builds' own dependencies through the JetBrains Maven Central mirror,
  // to avoid HTTP Error 429 Too Many Requests in the CI (SCL-25750).
  override protected def overrideBuildRepositories: Boolean = true

  override protected def getTestDataProjectPath: String =
    generateTestProjectPath(getTestName(true))

  override protected def getTestProjectCopyOptions: TestProjectCopyOptions =
    super.getTestProjectCopyOptions.copy(copyToTemporaryDir = true)

  override def createImportSpec() = {
    val importSpecBuilder = new ImportSpecBuilder(super.createImportSpec())
    if (isPreview) {
      importSpecBuilder.usePreviewMode()
    }
    importSpecBuilder.build()
  }

  protected lazy val projectStructureAssertions: ProjectStructureAssertionsFixture =
    new ProjectStructureAssertionsFixture(getMyProject)

  protected lazy val sbtDirectoryCompletionFixture: SbtDirectoryCompletionFixture =
    new SbtDirectoryCompletionFixture(getMyProject)

  // Export it to be able to use without import in subtests
  export org.jetbrains.sbt.project.directoryCompletion.ExpectedDirectoryCompletionVariant
  export org.jetbrains.sbt.project.directoryCompletion.SbtExpectedDirectoryCompletionRoots.*

  protected implicit lazy val defaultCompareContext: ProjectStructureComparisonContext =
    projectStructureAssertions.defaultCompareContext

  protected def runTest(expected: project): Unit =
    runTest(expected, identity)

  protected def runTest(expected: project, optionsModifier: ProjectComparisonOptions => ProjectComparisonOptions, mutedNotificationTitles: Seq[String] = Seq.empty): Unit = {
    val notificationsCollector = projectStructureAssertions.subscribeOnWarningsAndErrors()

    importProject(false)

    val projectData = ProjectDataManager.getInstance.getExternalProjectsData(getMyProject, getExternalSystemId).asScala.toSeq
    projectData match {
      case Nil =>
        fail("Couldn't import project (project data is empty). See output for the details.")
      case infos =>
        val withEmptyStructure = infos.find(_.getExternalProjectStructure == null)
        withEmptyStructure.foreach { pd =>
          fail(s"Couldn't import project (structure is empty). See output for the details. Project: $pd")
        }
    }

    // Always check the project dependencies order in the main/test modules mode
    val separateProdAndTestSources = getTestSbtProjectSettings.separateProdAndTestSources
    val compareContext = defaultCompareContext.withOptions(optionsModifier).withOptions(_.copy(checkProjectDependenciesOrder = separateProdAndTestSources))
    projectStructureAssertions.assertProjectsEqual(expected, !separateProdAndTestSources)(using compareContext)
    projectStructureAssertions.assertNoNotificationsShown(notificationsCollector.getNotifications, mutedNotificationTitles)
  }

  protected def generateTestProjectPath(projectName: String): String =
    s"${TestUtils.getTestDataPath}/sbt/projects/$projectName"

  //NOTE: it doesn't test final ordering on UI, see IDEA-306694
  protected def assertSbtDirectoryCompletionContributorVariants(
    directory: VirtualFile,
    expectedVariants: Seq[ExpectedDirectoryCompletionVariant]
  ): Unit =
    sbtDirectoryCompletionFixture.assertVariants(directory, expectedVariants)

  protected def assertDirectoryCompletionVariantsForProjectPaths(
    expectedSbtCompletionVariantsForParentModule: Seq[ExpectedDirectoryCompletionVariant],
    expectedSbtCompletionVariantsForMainModule: Seq[ExpectedDirectoryCompletionVariant],
    expectedSbtCompletionVariantsForTestModule: Seq[ExpectedDirectoryCompletionVariant],
    projectPaths: String*
  ): Unit =
    sbtDirectoryCompletionFixture.assertVariantsForProjectPaths(
      expectedSbtCompletionVariantsForParentModule,
      expectedSbtCompletionVariantsForMainModule,
      expectedSbtCompletionVariantsForTestModule,
      projectPaths,
      findVirtualFile
    )

  protected def findVirtualFile(projectPath: String): VirtualFile = {
    val vfm = VirtualFileManager.getInstance()
    val projectPathVirtualFile = vfm.findFileByNioPath(Path.of(projectPath))
    Assert.assertNotNull(s"VirtualFile for $projectPath is null", projectPathVirtualFile)
    projectPathVirtualFile
  }

  protected def setSbtSettingsCustomSdk(sdk: Sdk): Unit = {
    val settings = SbtSettings.getInstance(getMyProject)
    settings.setCustomVMPath(sdk.getHomePath.ensuring(_ != null))
  }

  protected def setOptions(project: Project, source: LanguageLevel, target: String, other: Seq[String]): Unit =
    JavaCompilerOptionsUtils.setProjectOptions(project, source, target, other)

  protected def setOptions(module: Module, source: LanguageLevel, target: String, other: Seq[String]): Unit =
    JavaCompilerOptionsUtils.setModuleOptions(module, source, target, other)

  protected def commonSourceResourceAndTargetDirs(module: module): Unit =
    ProjectStructureDslTestUtils.commonSourceResourceAndTargetDirs(module)

  protected def emptySourceResourceDirs(module: module): Unit =
    ProjectStructureDslTestUtils.emptySourceResourceDirs(module)

  protected def emptySourceResourceDirsMain(module: module): Unit =
    ProjectStructureDslTestUtils.emptySourceResourceDirsMain(module)

  protected def emptySourceResourceDirsTest(module: module): Unit =
    ProjectStructureDslTestUtils.emptySourceResourceDirsTest(module)

  protected def buildCrossProjectAndAssertNoWarningsOrErrors(): Unit =
    CompilerUtils.buildCrossProjectAndAssertNoWarningsOrErrors(getMyProject)

  protected def createModuleWithSourceSet(moduleName: String, group: Array[String] = null): Seq[module] =
    ProjectStructureDslTestUtils.createModuleWithSourceSet(moduleName, group)

  protected def standardRoots(relativePath: String, scope: String, scalaVersion: String = "2.13"): Seq[String] =
    ProjectStructureDslTestUtils.standardRoots(relativePath, scope, scalaVersion)

  protected def buildProjectAndAssertNoWarningsOrErrors(): Unit =
    CompilerUtils.buildProjectAndAssertNoWarningsOrErrors(getMyProject)
}
