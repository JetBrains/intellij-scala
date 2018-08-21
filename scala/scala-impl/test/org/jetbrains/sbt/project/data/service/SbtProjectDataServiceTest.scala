package org.jetbrains.sbt.project.data.service

import java.io.File

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.{LanguageLevelProjectExtension, ProjectRootManager}
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.{IdeaTestUtil, UsefulTestCase}
import org.jetbrains.plugins.scala.project.{IncrementalityType, external}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.sbt.project.data
import org.jetbrains.sbt.project.data.{ModuleNode, SbtProjectData, SbtProjectNode}
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.project.sources.SharedSourcesModuleType
import org.jetbrains.sbt.settings.SbtSettings
import org.junit.Assert._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.external.{JdkByName, SdkReference, SdkUtils}

/**
 * @author Nikolay Obedin
 * @since 6/15/15.
 */
class SbtProjectDataServiceTest extends ProjectDataServiceTestCase {

  import ExternalSystemDataDsl._

  override def setUp(): Unit = {
    super.setUp()
    setUpJdks()
  }

  override def tearDown(): Unit = {
    tearDownJdks()
    super.tearDown()
  }

  def testEmptyBasePackages(): Unit =
    doTestBasePackages(Seq.empty)

  def testNonEmptyBasePackages(): Unit =
    doTestBasePackages(Seq("com.test1.base", "com.test2.base"))

  def testValidJavaSdk(): Unit =
    doTestSdk(Option(JdkByName("1.8")),
      ProjectJdkTable.getInstance().findJdk(IdeaTestUtil.getMockJdk18.getName),
      LanguageLevel.JDK_1_8)

  def testValidJavaSdkWithDifferentLanguageLevel(): Unit =
    doTestSdk(Option(external.JdkByName("1.8")),
      Seq("-source", "1.6"),
      ProjectJdkTable.getInstance().findJdk(IdeaTestUtil.getMockJdk18.getName),
      LanguageLevel.JDK_1_6)

  def testInvalidSdk(): Unit =
    doTestSdk(Some(external.JdkByName("20")), defaultJdk, LanguageLevel.JDK_1_8)

  def testAbsentSdk(): Unit =
    doTestSdk(None, defaultJdk, LanguageLevel.JDK_1_8)

  def testJavacOptions(): Unit = {
    val options = Seq(
      "-g:none",
      "-nowarn",
      "-deprecation",
      "-target", "1.8",
      "-Werror"
    )
    importProjectData(generateProject(Seq.empty, None, options, ""))

    val compilerOptions = JavacConfiguration.getOptions(getProject, classOf[JavacConfiguration])
    assertFalse(compilerOptions.DEBUGGING_INFO)
    assertTrue(compilerOptions.GENERATE_NO_WARNINGS)
    assertTrue(compilerOptions.DEPRECATION)
    assertTrue(compilerOptions.ADDITIONAL_OPTIONS_STRING == "-Werror")

    val compilerConfiguration = CompilerConfiguration.getInstance(getProject)
    assertEquals("1.8", compilerConfiguration.getProjectBytecodeTarget)
  }

  def testSbtVersion(): Unit = {
    val projectSettings = SbtProjectSettings.default
    projectSettings.setExternalProjectPath(ExternalSystemApiUtil.normalizePath(getProject.getBasePath))
    SbtSettings.getInstance(getProject).linkProject(projectSettings)

    val expectedVersion = "0.13.8"
    importProjectData(generateProject(Seq.empty, None, Seq.empty, expectedVersion))
    val actualVersion = SbtSettings.getInstance(getProject).getLinkedProjectSettings(getProject.getBasePath).sbtVersion
    assertEquals(expectedVersion, actualVersion)
  }

  def testIncrementalityTypeForSharedModules(): Unit = {
    val testProject = new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath

      modules += new module {
        val uri = new File(getProject.getBasePath).toURI
        val moduleName = "Module 1"
        val typeId: String = SharedSourcesModuleType.instance.getId
        projectId := ModuleNode.combinedId(moduleName, Option(uri))
        projectURI := uri
        name := moduleName
        moduleFileDirectoryPath := getProject.getBasePath + "/module1"
        externalConfigPath := getProject.getBasePath + "/module1"
      }

      arbitraryNodes += new SbtProjectNode(SbtProjectData(Seq.empty, None, Seq.empty, "", getProject.getBasePath))
    }.build.toDataNode

    importProjectData(testProject)
    assertEquals(IncrementalityType.SBT, ScalaCompilerConfiguration.instanceIn(getProject).incrementalityType)
  }

  private def setUpJdks(): Unit = inWriteAction {
    val projectJdkTable = ProjectJdkTable.getInstance()
    projectJdkTable.getAllJdks.foreach(projectJdkTable.removeJdk)
    projectJdkTable.addJdk(IdeaTestUtil.getMockJdk17)
    projectJdkTable.addJdk(IdeaTestUtil.getMockJdk18)
    // TODO: find a way to create mock Android SDK
  }

  private def tearDownJdks(): Unit = inWriteAction {
    val projectJdkTable = ProjectJdkTable.getInstance()
    projectJdkTable.getAllJdks.foreach(projectJdkTable.removeJdk)
  }

  private def generateProject(basePackages: Seq[String], jdk: Option[SdkReference], javacOptions: Seq[String], sbtVersion: String): DataNode[ProjectData] =
    new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath

      arbitraryNodes += new SbtProjectNode(SbtProjectData(basePackages, jdk, javacOptions, sbtVersion, getProject.getBasePath))
    }.build.toDataNode

  private def doTestBasePackages(basePackages: Seq[String]): Unit = {
    importProjectData(generateProject(basePackages, None, Seq.empty, ""))
    UsefulTestCase.assertContainsElements(ScalaProjectSettings.getInstance(getProject).getBasePackages, basePackages:_*)
  }

  private def defaultJdk: Sdk =
    ProjectJdkTable.getInstance().findMostRecentSdkOfType(JavaSdk.getInstance())

  private def doTestSdk(sdk: Option[SdkReference], expectedSdk: Sdk, expectedLanguageLevel: LanguageLevel): Unit =
    doTestSdk(sdk, Seq.empty, expectedSdk, expectedLanguageLevel)

  private def doTestSdk(sdk: Option[SdkReference], javacOptions: Seq[String], expectedSdk: Sdk, expectedLanguageLevel: LanguageLevel): Unit = {
    importProjectData(generateProject(Seq.empty, sdk, javacOptions, ""))
    assertEquals(expectedSdk, ProjectRootManager.getInstance(getProject).getProjectSdk)
    val languageLevelProjectExtension = LanguageLevelProjectExtension.getInstance(getProject)
    val actualLanguageLevel = languageLevelProjectExtension.getLanguageLevel
    assertEquals(expectedLanguageLevel,actualLanguageLevel)
    if (SdkUtils.defaultJavaLanguageLevelIn(expectedSdk).fold(false)(_ != expectedLanguageLevel))
      assertFalse(languageLevelProjectExtension.getDefault)
  }
}
