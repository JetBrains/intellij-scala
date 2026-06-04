package org.jetbrains.sbt.project

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.{LanguageLevelModuleExtension, LanguageLevelProjectExtension, ModuleRootModificationUtil}
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiManager
import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.plugins.scala.compiler.testUtils.CompilerUtils
import org.jetbrains.plugins.scala.notifications.CollectingNotificationsListener
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.sbt.actions.SbtDirectoryCompletionContributor
import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase.TestProjectCopyOptions
import org.jetbrains.sbt.project.utils.{ProjectComparisonOptions, ProjectStructureComparisonContext}
import org.jetbrains.sbt.settings.SbtSettings
import org.junit.Assert
import org.junit.Assert.fail

import java.nio.file.Path
import scala.jdk.CollectionConverters.{CollectionHasAsScala, SeqHasAsJava}

/**
 * Base class for tests that verify the IDE project model produced by an sbt import.
 *
 * The sbt-specific external-system setup is provided by [[SbtExternalSystemImportingTestLike]].
 *
 * This class builds on that setup and adds the project-structure testing layer:
 *
 *  - [[ProjectStructureMatcher]] and [[ExactMatch]] for describing and comparing the expected IDE project layout
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
abstract class SbtProjectStructureImportingLike extends SbtExternalSystemImportingTestLike
  with ProjectStructureMatcher
  with ExactMatch {

  import ProjectStructureDsl._

  /**
   * When set to `true` during a test run, only a preview import is performed.
   */
  protected def isPreview: Boolean = false

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

  protected implicit lazy val defaultCompareContext: ProjectStructureComparisonContext =
    ProjectStructureComparisonContext.Implicit.default(using getMyProject)

  protected def runTest(expected: project): Unit =
    runTest(expected, identity)

  protected def runTest(expected: project, optionsModifier: ProjectComparisonOptions => ProjectComparisonOptions, mutedNotificationTitles: Seq[String] = Seq.empty): Unit = {
    val notificationsCollector = CollectingNotificationsListener.subscribeOnWarningsAndErrors(getMyProject)

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
    assertProjectsEqual(expected, getMyProject, !separateProdAndTestSources)(using compareContext)
    assertNoNotificationsShown(getMyProject, notificationsCollector.getNotifications, mutedNotificationTitles)
  }

  protected def generateTestProjectPath(projectName: String): String =
    s"${TestUtils.getTestDataPath}/sbt/projects/$projectName"

  protected case class ExpectedDirectoryCompletionVariant(
    projectRelativePath: String,
    rootType: JpsModuleSourceRootType[?]
  )
  object ExpectedDirectoryCompletionVariant {
    implicit val expectedDirectoryCompletionVariantOrdering: Ordering[ExpectedDirectoryCompletionVariant] =
      (x: ExpectedDirectoryCompletionVariant, y: ExpectedDirectoryCompletionVariant) => {
        x.projectRelativePath compare y.projectRelativePath
      }
  }

  protected val DefaultSbtContentRootsScala212: Seq[ExpectedDirectoryCompletionVariant] =
    defaultSbtContentRootsScala2("2.12")

  protected val DefaultSbtContentRootsScala213: Seq[ExpectedDirectoryCompletionVariant] =
    defaultSbtContentRootsScala2("2.13")

  private def defaultSbtContentRootsScala2(scalaBinVer: String): Seq[ExpectedDirectoryCompletionVariant] = Seq(
    ("src/main/java", JavaSourceRootType.SOURCE),
    ("src/main/scala", JavaSourceRootType.SOURCE),
    ("src/main/scala-2", JavaSourceRootType.SOURCE),
    (s"src/main/scala-$scalaBinVer", JavaSourceRootType.SOURCE),
    ("src/test/java", JavaSourceRootType.TEST_SOURCE),
    ("src/test/scala", JavaSourceRootType.TEST_SOURCE),
    ("src/test/scala-2", JavaSourceRootType.TEST_SOURCE),
    (s"src/test/scala-$scalaBinVer", JavaSourceRootType.TEST_SOURCE),
    ("src/main/resources", JavaResourceRootType.RESOURCE),
    ("src/test/resources", JavaResourceRootType.TEST_RESOURCE),
  ).map(ExpectedDirectoryCompletionVariant.apply.tupled)

  protected val DefaultMainSbtContentRootsScala213: Seq[ExpectedDirectoryCompletionVariant] =
    defaultMainSbtContentRootsScala2(13)

  protected val DefaultTestSbtContentRootsScala213: Seq[ExpectedDirectoryCompletionVariant] =
    defaultTestSbtContentRootsScala2(13)

  protected val DefaultMainSbtContentRootsScala212: Seq[ExpectedDirectoryCompletionVariant] =
    defaultMainSbtContentRootsScala2(12)

  protected val DefaultTestSbtContentRootsScala212: Seq[ExpectedDirectoryCompletionVariant] =
    defaultTestSbtContentRootsScala2(12)


  private def defaultMainSbtContentRootsScala2: Integer => Seq[ExpectedDirectoryCompletionVariant] = (minorVersion: Integer) => Seq(
    ("java", JavaSourceRootType.SOURCE),
    ("scala", JavaSourceRootType.SOURCE),
    ("scala-2", JavaSourceRootType.SOURCE),
    (s"scala-2.$minorVersion", JavaSourceRootType.SOURCE),
    ("resources", JavaResourceRootType.RESOURCE),
  ).map(ExpectedDirectoryCompletionVariant.apply.tupled)

  private def defaultTestSbtContentRootsScala2: Integer => Seq[ExpectedDirectoryCompletionVariant] = (minorVersion: Integer) => Seq(
    ("java", JavaSourceRootType.TEST_SOURCE),
    ("scala", JavaSourceRootType.TEST_SOURCE),
    ("scala-2", JavaSourceRootType.TEST_SOURCE),
    (s"scala-2.$minorVersion", JavaSourceRootType.TEST_SOURCE),
    ("resources", JavaResourceRootType.TEST_RESOURCE),
  ).map(ExpectedDirectoryCompletionVariant.apply.tupled)

  protected val DefaultSbtContentRootsScala3: Seq[ExpectedDirectoryCompletionVariant] = Seq(
    ("src/main/java", JavaSourceRootType.SOURCE),
    ("src/main/scala", JavaSourceRootType.SOURCE),
    ("src/main/scala-3", JavaSourceRootType.SOURCE),
    ("src/test/java", JavaSourceRootType.TEST_SOURCE),
    ("src/test/scala", JavaSourceRootType.TEST_SOURCE),
    ("src/test/scala-3", JavaSourceRootType.TEST_SOURCE),
    ("src/main/resources", JavaResourceRootType.RESOURCE),
    ("src/test/resources", JavaResourceRootType.TEST_RESOURCE),
  ).map(ExpectedDirectoryCompletionVariant.apply.tupled)

  protected val DefaultMainSbtContentRootsScala3: Seq[ExpectedDirectoryCompletionVariant] = Seq(
    ("java", JavaSourceRootType.SOURCE),
    ("scala", JavaSourceRootType.SOURCE),
    ("scala-3", JavaSourceRootType.SOURCE),
    ("resources", JavaResourceRootType.RESOURCE),
  ).map(ExpectedDirectoryCompletionVariant.apply.tupled)

  protected val DefaultTestSbtContentRootsScala3: Seq[ExpectedDirectoryCompletionVariant] = Seq(
    ("java", JavaSourceRootType.TEST_SOURCE),
    ("scala", JavaSourceRootType.TEST_SOURCE),
    ("scala-3", JavaSourceRootType.TEST_SOURCE),
    ("resources", JavaResourceRootType.TEST_RESOURCE),
  ).map(ExpectedDirectoryCompletionVariant.apply.tupled)

  //NOTE: it doesn't test final ordering on UI, see IDEA-306694
  protected def assertSbtDirectoryCompletionContributorVariants(
    directory: VirtualFile,
    expectedVariants: Seq[ExpectedDirectoryCompletionVariant]
  ): Unit = {
    val psiDirectory = PsiManager.getInstance(getMyProject).findDirectory(directory)
    val directoryPath = directory.getPath

    val variants = new SbtDirectoryCompletionContributor().getVariants(psiDirectory).asScala.toSeq
    val actualVariants = variants.map(v => ExpectedDirectoryCompletionVariant(
      v.getPath.stripPrefix(directoryPath).stripPrefix("/"),
      v.getRootType
    ))

    assertCollectionEquals(
      "Wrong directory completion contributor variants",
      expectedVariants.sorted,
      actualVariants.sorted
    )
  }

  protected def assertDirectoryCompletionVariantsForProjectPaths(
    expectedSbtCompletionVariantsForParentModule: Seq[ExpectedDirectoryCompletionVariant],
    expectedSbtCompletionVariantsForMainModule: Seq[ExpectedDirectoryCompletionVariant],
    expectedSbtCompletionVariantsForTestModule: Seq[ExpectedDirectoryCompletionVariant],
    projectPaths: String*
  ): Unit =
    projectPaths.foreach { projectPath =>
      Seq(
        (projectPath, expectedSbtCompletionVariantsForParentModule),
        (s"$projectPath/src/main", expectedSbtCompletionVariantsForMainModule),
        (s"$projectPath/src/test", expectedSbtCompletionVariantsForTestModule)
      ).foreach { case (path, variants) =>
        assertSbtDirectoryCompletionContributorVariants(findVirtualFile(path), variants)
      }
    }

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

  protected def setOptions(project: Project, source: LanguageLevel, target: String, other: Seq[String]): Unit = {
    val compilerSettings = CompilerConfiguration.getInstance(project)
    compilerSettings.setProjectBytecodeTarget(target)

    val options = JavacConfiguration.getOptions(project, classOf[JavacConfiguration])
    options.ADDITIONAL_OPTIONS_STRING = other.mkString(" ")

    val ext = LanguageLevelProjectExtension.getInstance(project)
    ext.setLanguageLevel(source)
  }

  protected def setOptions(module: Module, source: LanguageLevel, target: String, other: Seq[String]): Unit = {
    val compilerSettings = CompilerConfiguration.getInstance(module.getProject)
    compilerSettings.setBytecodeTargetLevel(module, target)
    compilerSettings.setAdditionalOptions(module, other.asJava)

    ModuleRootModificationUtil.updateModel(module,
      _.getModuleExtension(classOf[LanguageLevelModuleExtension]).setLanguageLevel(source)
    )
  }

  protected def commonSourceResourceAndTargetDirs(module: module): Unit = {
    import module._
    ProjectStructureDsl.sources := Seq("src/main/scala", "src/main/java")
    ProjectStructureDsl.testSources := Seq("src/test/scala", "src/test/java")
    ProjectStructureDsl.resources := Seq("src/main/resources")
    ProjectStructureDsl.testResources := Seq("src/test/resources")
    ProjectStructureDsl.excluded := Seq("target")
  }

  protected def emptySourceResourceDirs(module: module): Unit = {
    emptySourceResourceDirsMain(module)
    emptySourceResourceDirsTest(module)
  }

  protected def emptySourceResourceDirsMain(module: module): Unit = {
    import module._
    ProjectStructureDsl.sources := Nil
    ProjectStructureDsl.resources := Nil
  }

  protected def emptySourceResourceDirsTest(module: module): Unit = {
    import module._
    ProjectStructureDsl.testSources := Nil
    ProjectStructureDsl.testResources := Nil
  }

  protected def buildCrossProjectAndAssertNoWarningsOrErrors(): Unit = {
    CompilerUtils.buildCrossProjectAndAssertNoWarningsOrErrors(getMyProject)
  }

  protected def createModuleWithSourceSet(moduleName: String, group: Array[String] = null): Seq[module] =
    Seq(moduleName, s"$moduleName.main", s"$moduleName.test").map { name =>
      new module(name, group)
    }

  protected def standardRoots(relativePath: String, scope: String, scalaVersion: String = "2.13"): Seq[String] = {
    val normalized = if (relativePath.isEmpty) "" else s"$relativePath/"
    Seq(
      s"%PROJECT_ROOT%/${normalized}src/$scope",
      s"%PROJECT_ROOT%/${normalized}target/scala-$scalaVersion/src_managed/$scope",
      s"%PROJECT_ROOT%/${normalized}target/scala-$scalaVersion/resource_managed/$scope"
    )
  }

  protected def buildProjectAndAssertNoWarningsOrErrors(): Unit =
    CompilerUtils.buildProjectAndAssertNoWarningsOrErrors(getMyProject)
}
