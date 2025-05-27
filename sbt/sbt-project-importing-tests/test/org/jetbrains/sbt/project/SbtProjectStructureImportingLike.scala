package org.jetbrains.sbt.project

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.compiler.{CompilerMessage, CompilerMessageCategory}
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.{LanguageLevelModuleExtension, LanguageLevelProjectExtension, ModuleRootModificationUtil}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiManager
import com.intellij.testFramework.CompilerTester
import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfigurable, ScalaCompilerConfiguration, ScalaCompilerSettings}
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.sbt.actions.SbtDirectoryCompletionContributor
import org.jetbrains.sbt.project.SbtProjectStructureImportingLike.CollectingNotificationsListener
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.project.utils.{ProjectComparisonOptions, ProjectStructureComparisonContext}
import org.jetbrains.sbt.settings.SbtSettings
import org.junit.Assert
import org.junit.Assert.fail

import java.io.File
import java.nio.file.Path
import scala.collection.mutable
import scala.jdk.CollectionConverters.{CollectionHasAsScala, SeqHasAsJava}

abstract class SbtProjectStructureImportingLike extends SbtExternalSystemImportingTestLike
  with ProjectStructureMatcher
  with ExactMatch {

  import ProjectStructureDsl._

  override protected def getTestDataProjectPath: String =
    generateTestProjectPath(getTestName(true))

  override protected def copyTestProjectToTemporaryDir: Boolean = true

  override def setUp(): Unit = {
    super.setUp()
    SbtProjectResolver.processOutputOfLatestStructureDump = ""
    SbtCachesSetupUtil.setupCoursierAndIvyCache(getProject)
  }

  protected implicit lazy val defaultCompareContext: ProjectStructureComparisonContext =
    ProjectStructureComparisonContext.Implicit.default(getProject)

  protected def runTest(expected: project): Unit =
    runTest(expected, identity)

  protected def runTest(expected: project, optionsModifier: ProjectComparisonOptions => ProjectComparisonOptions): Unit = {
    val notificationsCollector = new CollectingNotificationsListener(Set(NotificationType.WARNING, NotificationType.ERROR))
    getProject.getMessageBus.connect().subscribe(Notifications.TOPIC, notificationsCollector)

    importProject(false)

    val projectData = ProjectDataManager.getInstance.getExternalProjectsData(getProject, getExternalSystemId).asScala.toSeq
    projectData match {
      case Nil =>
        fail("Couldn't import project (project data is empty). See output for the details.")
      case infos =>
        val withEmptyStructure = infos.find(_.getExternalProjectStructure == null)
        withEmptyStructure.foreach { pd =>
          fail(s"Couldn't import project (structure is empty). See output for the details. Project: $pd")
        }
    }


    assertProjectsEqual(expected, myProject, !enableSeparateModulesForProdTest)(defaultCompareContext.withOptions(optionsModifier))
    assertNoNotificationsShown(myProject, notificationsCollector.getNotifications)
  }

  /**
   * It is necessary to explicitly set all project settings that are tested/required for test, because what is set in
   * #setUp method in each SbtProjectStructureImportingTest classes is not applied to the project settings of the linked project
   */
  protected def linkSbtProject(path: String, prodTestSourcesSeparated: Boolean): Unit = {
    val settings = new SbtProjectSettings
    settings.jdk = getJdkConfiguredForTestCase.getName
    settings.setExternalProjectPath(path)
    settings.setSeparateProdAndTestSources(prodTestSourcesSeparated)
    SbtSettings.getInstance(myProject).linkProject(settings)
  }

  protected def generateTestProjectPath(projectName: String): String =
    s"${TestUtils.getTestDataPath}/sbt/projects/$projectName"

  protected case class ExpectedDirectoryCompletionVariant(
    projectRelativePath: String,
    rootType: JpsModuleSourceRootType[_]
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
  ).map((ExpectedDirectoryCompletionVariant.apply _).tupled)

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
  ).map((ExpectedDirectoryCompletionVariant.apply _).tupled)

  private def defaultTestSbtContentRootsScala2: Integer => Seq[ExpectedDirectoryCompletionVariant] = (minorVersion: Integer) => Seq(
    ("java", JavaSourceRootType.TEST_SOURCE),
    ("scala", JavaSourceRootType.TEST_SOURCE),
    ("scala-2", JavaSourceRootType.TEST_SOURCE),
    (s"scala-2.$minorVersion", JavaSourceRootType.TEST_SOURCE),
    ("resources", JavaResourceRootType.TEST_RESOURCE),
  ).map((ExpectedDirectoryCompletionVariant.apply _).tupled)

  protected val DefaultSbtContentRootsScala3: Seq[ExpectedDirectoryCompletionVariant] = Seq(
    ("src/main/java", JavaSourceRootType.SOURCE),
    ("src/main/scala", JavaSourceRootType.SOURCE),
    ("src/main/scala-3", JavaSourceRootType.SOURCE),
    ("src/test/java", JavaSourceRootType.TEST_SOURCE),
    ("src/test/scala", JavaSourceRootType.TEST_SOURCE),
    ("src/test/scala-3", JavaSourceRootType.TEST_SOURCE),
    ("src/main/resources", JavaResourceRootType.RESOURCE),
    ("src/test/resources", JavaResourceRootType.TEST_RESOURCE),
  ).map((ExpectedDirectoryCompletionVariant.apply _).tupled)

  protected val DefaultMainSbtContentRootsScala3: Seq[ExpectedDirectoryCompletionVariant] = Seq(
    ("java", JavaSourceRootType.SOURCE),
    ("scala", JavaSourceRootType.SOURCE),
    ("scala-3", JavaSourceRootType.SOURCE),
    ("resources", JavaResourceRootType.RESOURCE),
  ).map((ExpectedDirectoryCompletionVariant.apply _).tupled)

  protected val DefaultTestSbtContentRootsScala3: Seq[ExpectedDirectoryCompletionVariant] = Seq(
    ("java", JavaSourceRootType.TEST_SOURCE),
    ("scala", JavaSourceRootType.TEST_SOURCE),
    ("scala-3", JavaSourceRootType.TEST_SOURCE),
    ("resources", JavaResourceRootType.TEST_RESOURCE),
  ).map((ExpectedDirectoryCompletionVariant.apply _).tupled)

  //NOTE: it doesn't test final ordering on UI, see IDEA-306694
  protected def assertSbtDirectoryCompletionContributorVariants(
    directory: VirtualFile,
    expectedVariants: Seq[ExpectedDirectoryCompletionVariant]
  ): Unit = {
    val psiDirectory = PsiManager.getInstance(myProject).findDirectory(directory)
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

  protected def findVirtualFile(projectPath: String): VirtualFile = {
    val vfm = VirtualFileManager.getInstance()
    val projectPathVirtualFile = vfm.findFileByNioPath(Path.of(projectPath))
    Assert.assertNotNull(s"VirtualFile for $projectPath is null", projectPathVirtualFile)
    projectPathVirtualFile
  }

  protected def setSbtSettingsCustomSdk(sdk: Sdk): Unit = {
    val settings = SbtSettings.getInstance(myProject)
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

  protected def injectVariable(file: File, variableName: String, value: String): Unit = {
    val fileContent = FileUtil.loadFile(file)
    val updatedContent = fileContent.replace(variableName, value)
    FileUtil.writeToFile(file, updatedContent)
  }

  protected def buildCrossProjectAndAssertNoWarningsOrErrors(): Unit = {
    val compilerConfiguration = ScalaCompilerConfiguration.instanceIn(getProject)
    val incrementalityType = compilerConfiguration.incrementalityType
    Assert.assertEquals(
      s"Cross-built projects with shared sources should have ${IncrementalityType.SBT} incrementality type",
      IncrementalityType.SBT,
      incrementalityType
    )
    buildProjectAndAssertNoWarningsOrErrors()
  }

  protected def buildProjectAndAssertNoWarningsOrErrors(): Unit = {
    val compilerConfiguration = ScalaCompilerConfiguration.instanceIn(getProject)
    val incrementalityType = compilerConfiguration.incrementalityType

    val modules = ModuleManager.getInstance(getProject).getModules
    val compiler = new CompilerTester(getProject, java.util.Arrays.asList(modules: _*), null, false)

    def buildMessageText(message: CompilerMessage): String = {
      s"""[${message.getCategory}] ${message.getVirtualFile}
         |${message.getMessage}""".stripMargin
    }

    try {
      val messages = compiler.rebuild().asScala.toSeq
      val warningsOrErrors: Seq[CompilerMessage] = messages.filter(m => Set(CompilerMessageCategory.ERROR, CompilerMessageCategory.WARNING).contains(m.getCategory))
      Assert.assertEquals(
        s"Expecting no compilation warnings or errors (with $incrementalityType incremental compiler)",
        "",
        warningsOrErrors.map(buildMessageText).mkString("\n")
      )
    } finally {
      // Manually clean up compiler-related allocated resources to prevent resource leaks after test end
      compiler.tearDown()
      CompileServerLauncher.stopServerAndWait()
    }
  }
}

object SbtProjectStructureImportingLike {

  //TODO: move it to some base class and call assertNoNotificationsShown in more tests (BSP/Maven/Gradle/new project wizard)
  private class CollectingNotificationsListener(types: Set[NotificationType]) extends Notifications {
    private val notifications = mutable.ArrayBuffer[Notification]()

    def getNotifications: Seq[Notification] = notifications.toSeq

    override def notify(notification: Notification): Unit = {
      if (types.contains(notification.getType)) {
        notifications += notification
      }
    }
  }
}
