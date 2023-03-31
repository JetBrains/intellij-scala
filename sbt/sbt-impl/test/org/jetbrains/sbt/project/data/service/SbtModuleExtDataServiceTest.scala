package org.jetbrains.sbt.project.data.service

import com.intellij.compiler.CompilerConfiguration
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.module.{JavaModuleType, LanguageLevelUtil, Module, ModuleManager, ModuleType}
import com.intellij.openapi.projectRoots
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.{IdeaTestUtil, UsefulTestCase}
import org.jetbrains.plugins.scala.compiler.data.DebuggingInfoLevel
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.external.{JdkByHome, JdkByName, SdkReference, SdkUtils}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.sbt.project.data._
import org.junit.Assert._

import java.io.File
import java.net.URI
import scala.jdk.CollectionConverters._

class SbtModuleExtDataServiceTest extends ProjectDataServiceTestCase {

  import ExternalSystemDataDsl._

  override def setUp(): Unit = {
    super.setUp()
    setUpJdks()
  }

  override def tearDown(): Unit = {
    tearDownJdks()
    super.tearDown()
  }

  def testWithoutScalaLibrary(): Unit =
    importProjectData(generateScalaProject("2.11.5", None, Seq.empty))

  def testWithIncompatibleScalaLibrary(): Unit = {
    importProjectData(generateScalaProject("2.11.5", Some("2.10.4"), Seq.empty))
    //assertScalaLibraryWarningNotificationShown(getProject, SbtProjectSystem.Id)
    assertNoNotificationShown(getProject)
  }

  def testWithCompatibleScalaLibrary(): Unit = {
    doTestAndCheckScalaSdk("2.11.1", "2.11.5")
    doTestAndCheckScalaSdk("2.10.4", "2.10.3")
  }

  def testWithTheSameVersionOfScalaLibrary(): Unit = {
    doTestAndCheckScalaSdk("2.11.6", "2.11.6")
    doTestAndCheckScalaSdk("2.10.4", "2.10.4")
    doTestAndCheckScalaSdk("2.9.2", "2.9.2")
  }

  def testCompilerOptionsSetup(): Unit = {
    val options = Seq(
      "-g:source",
      "-Xplugin:test-plugin.jar",
      "-Xexperimental",
      "-P:continuations:enable",
      "-deprecation",
      "-language:dynamics",
      "-language:existentials",
      "-explaintypes",
      "-feature",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:macros",
      "-optimise",
      "-language:postfixOps",
      "-language:reflectiveCalls",
      "-no-specialization",
      "-unchecked",
      "-nowarn",
      "-XmyCoolAdditionalOption"
    )

    importProjectData(generateScalaProject("2.11.5", Some("2.11.5"), options))
    val module = ModuleManager.getInstance(getProject).findModuleByName("Module 1")
    val compilerConfiguration = ScalaCompilerConfiguration.instanceIn(getProject).getSettingsForModule(module)

    assertEquals(compilerConfiguration.debuggingInfoLevel, DebuggingInfoLevel.Source)
    assertTrue(compilerConfiguration.plugins.exists(_.endsWith("test-plugin.jar")))
    UsefulTestCase.assertContainsElements(compilerConfiguration.additionalCompilerOptions.asJava, "-XmyCoolAdditionalOption")
    assertTrue(compilerConfiguration.continuations)
    assertTrue(compilerConfiguration.experimental)
    assertTrue(compilerConfiguration.deprecationWarnings)
    assertTrue(compilerConfiguration.dynamics)
    assertTrue(compilerConfiguration.existentials)
    assertTrue(compilerConfiguration.explainTypeErrors)
    assertTrue(compilerConfiguration.featureWarnings)
    assertTrue(compilerConfiguration.higherKinds)
    assertTrue(compilerConfiguration.implicitConversions)
    assertTrue(compilerConfiguration.macros)
    assertTrue(compilerConfiguration.optimiseBytecode)
    assertTrue(compilerConfiguration.postfixOps)
    assertTrue(compilerConfiguration.reflectiveCalls)
    assertFalse(compilerConfiguration.specialization)
    assertTrue(compilerConfiguration.uncheckedWarnings)
    assertFalse(compilerConfiguration.warnings)
  }

  def testModuleIsNull(): Unit = {
    val testProject = new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath
      arbitraryNodes += new ModuleExtNode(SbtModuleExtData(Some("2.11.5")))
    }.build.toDataNode

    importProjectData(testProject)
  }

  def testValidJavaSdk(): Unit =
    doTestSdk(Some(JdkByName("1.8")),
      ProjectJdkTable.getInstance().findJdk(IdeaTestUtil.getMockJdk18.getName),
      LanguageLevel.JDK_1_8)

  def testValidJavaSdkWithDifferentLanguageLevel(): Unit =
    doTestSdk(Some(JdkByName("1.8")),
      Seq("-source", "1.6"),
      ProjectJdkTable.getInstance().findJdk(IdeaTestUtil.getMockJdk18.getName),
      LanguageLevel.JDK_1_6)

  def testInvalidSdk(): Unit =
    doTestSdk(Some(JdkByName("20")), defaultJdk, LanguageLevel.JDK_1_7)

  def testAbsentSdk(): Unit =
    doTestSdk(None, defaultJdk, LanguageLevel.JDK_1_7)

  def testValidJdkByHome(): Unit = {
    val jdk = ProjectJdkTable.getInstance().findJdk(IdeaTestUtil.getMockJdk18.getName)
    doTestSdk(Some(JdkByHome(new File(jdk.getHomePath))), jdk, LanguageLevel.JDK_1_8)
  }

  def testJavacOptions(): Unit = {
    val moduleOptions = Seq(
      "-g:none",
      "-nowarn",
      "-deprecation",
      "-target", "1.8",
      "-Werror"
    )
    importProjectData(generateJavaProject(None, moduleOptions))

    val compilerConfiguration = CompilerConfiguration.getInstance(getProject)
    assertEquals("1.8", compilerConfiguration.getBytecodeTargetLevel(getModule))
    assertEquals(
      Seq("-g:none", "-nowarn", "-deprecation", "-Werror"),
      compilerConfiguration.getAdditionalOptions(getModule).asScala.toSeq
    )
  }

  def testScalaSdkForEvictedVersion(): Unit = {
    import org.jetbrains.plugins.scala.project._

    val evictedVersion = "2.11.2"
    val newVersion = "2.11.6"

    val projectData = new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath
      arbitraryNodes += new SbtProjectNode(SbtProjectData(None, "", getProject.getBasePath))

      val evictedScalaLibrary: library = new library { name := s"org.scala-lang:scala-library:$evictedVersion" }
      val newScalaLibrary: library = new library { name := s"org.scala-lang:scala-library:$newVersion" }
      libraries ++= Seq(evictedScalaLibrary, newScalaLibrary)

      modules += new javaModule {
        val uri: URI = new File(getProject.getBasePath).toURI
        val moduleName = "Module 1"
        projectId := ModuleNode.combinedId(moduleName, Option(uri))
        projectURI := uri
        name := moduleName
        moduleFileDirectoryPath := getProject.getBasePath + "/module1"
        externalConfigPath := getProject.getBasePath + "/module1"
        libraryDependencies += newScalaLibrary
        arbitraryNodes += new ModuleExtNode(SbtModuleExtData(Some(evictedVersion)))
      }
    }.build.toDataNode

    importProjectData(projectData)

    val isScalaSdkSetUp = getProject.libraries
      .filter(_.isScalaSdk)
      .count(_.getName.contains(s"sbt: scala-sdk-$evictedVersion"))
    assertTrue(s"More or less than one ScalaSdk for $evictedVersion scala version is set up in the project libraries", isScalaSdkSetUp == 1)
  }

  private def generateScalaProject(
    scalaVersion: String,
    scalaLibraryVersion: Option[String],
    scalacOptions: Seq[String]
  ): DataNode[ProjectData] =
    generateProject(Some(scalaVersion), scalaLibraryVersion, scalacOptions, None, Seq.empty)

  private def generateJavaProject(sdk: Option[SdkReference], moduleJavacOptions: Seq[String]): DataNode[ProjectData] =
    generateProject(None, None, Seq.empty, sdk, moduleJavacOptions)

  private def generateProject(
    scalaVersion: Option[String],
    scalaLibraryVersion: Option[String],
    scalacOptions: Seq[String],
    sdk: Option[SdkReference],
    javacOptions: Seq[String]
  ): DataNode[ProjectData] =
    new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath
      arbitraryNodes += new SbtProjectNode(SbtProjectData(None, "", getProject.getBasePath))

      val scalaLibrary: Option[library] = scalaLibraryVersion.map { version =>
        new library { name := "org.scala-lang:scala-library:" + version }
      }
      scalaLibrary.foreach(libraries += _)

      modules += new javaModule {
        val uri: URI = new File(getProject.getBasePath).toURI
        val moduleName = "Module 1"
        projectId := ModuleNode.combinedId(moduleName, Option(uri))
        projectURI := uri
        name := moduleName
        moduleFileDirectoryPath := getProject.getBasePath + "/module1"
        externalConfigPath := getProject.getBasePath + "/module1"
        scalaLibrary.foreach(libraryDependencies += _)
        arbitraryNodes += new ModuleExtNode(SbtModuleExtData(scalaVersion, Seq.empty, Seq.empty, scalacOptions, sdk, javacOptions))
      }
    }.build.toDataNode

  private def doTestAndCheckScalaSdk(scalaVersion: String, scalaLibraryVersion: String): Unit = {
    import org.jetbrains.plugins.scala.project._
    importProjectData(generateScalaProject(scalaVersion, Some(scalaLibraryVersion), Seq.empty))

    checkModuleLevelScalaSdk()
    val isLibrarySetUp = getProject.libraries
      .filter(_.getName.contains(s"sbt: scala-sdk-$scalaVersion"))
      .exists(_.isScalaSdk)
    assertTrue(s"ScalaSdk for $scalaVersion scala version is not set up in the project libraries", isLibrarySetUp)
  }

  private def checkModuleLevelScalaSdk():Unit = {
    import org.jetbrains.plugins.scala.project._
    getProject.modules.filter(ModuleType.get(_).getName == JavaModuleType.getModuleName).foreach { module =>
      var moduleLevelScalaSdk = false
      module.libraries.filter(_.isScalaSdk).foreach { _ =>
        if (!moduleLevelScalaSdk) moduleLevelScalaSdk = true
        else assertFalse(s"ScalaSdk is set more than once in the module ${module.getName}", moduleLevelScalaSdk)
      }
      assertTrue(s"ScalaSdk is not set up in the module ${module.getName}", moduleLevelScalaSdk)
    }
  }

  private def doTestSdk(sdk: Option[SdkReference], expectedSdk: projectRoots.Sdk, expectedLanguageLevel: LanguageLevel): Unit =
    doTestSdk(sdk, Seq.empty, expectedSdk, expectedLanguageLevel)

  private def doTestSdk(sdk: Option[SdkReference], javacOptions: Seq[String], expectedSdk: projectRoots.Sdk, expectedLanguageLevel: LanguageLevel): Unit = {
    importProjectData(generateJavaProject(sdk, javacOptions))

    val moduleRootManager = ModuleRootManager.getInstance(getModule)
    if (sdk.flatMap(SdkUtils.findProjectSdk).isEmpty) {
      assertTrue(moduleRootManager.isSdkInherited)
    } else {
      assertEquals(expectedSdk, moduleRootManager.getSdk)
      val actualLanguageLevel = LanguageLevelUtil.getCustomLanguageLevel(getModule)
      assertEquals(expectedLanguageLevel, actualLanguageLevel)
    }
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

  private def defaultJdk: projectRoots.Sdk =
    ProjectJdkTable.getInstance().getAllJdks.head

  override def getModule: Module =
    ModuleManager.getInstance(getProject).findModuleByName("Module 1")
}
