package org.jetbrains.sbt.project

import java.io.File

import com.intellij.compiler.CompilerConfiguration
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, ExternalSystemException}
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.projectRoots
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.{LanguageLevelModuleExtensionImpl, ModuleRootManager}
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import junit.framework.Assert._
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.project.{DebuggingInfoLevel, Version}
import org.jetbrains.sbt.UsefulTestCaseHelper
import org.jetbrains.sbt.project.data._

import scala.collection.JavaConverters._

/**
 * @author Nikolay Obedin
 * @since 6/9/15.
 */
class ModuleExtDataServiceTest extends ProjectDataServiceTestCase with UsefulTestCaseHelper {

  import ExternalSystemDsl._

  override def setUp(): Unit = {
    super.setUp()
    setUpJdks()
  }

  def testWithoutScalaLibrary(): Unit =
    importProjectData(generateScalaProject("2.11.5", None, Seq.empty))

  def testWithIncompatibleScalaLibrary(): Unit =
    assertException[ExternalSystemException](Some("Cannot find project Scala library 2.11.5 for module Module 1")) {
      importProjectData(generateScalaProject("2.11.5", Some("2.10.4"), Seq.empty))
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
    assertContainsElements(compilerConfiguration.plugins.asJava, "test-plugin.jar")
    assertContainsElements(compilerConfiguration.additionalCompilerOptions.asJava, "-XmyCoolAdditionalOption")
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
      arbitraryNodes += new ModuleExtNode(Some(Version("2.11.5")), Seq.empty, Seq.empty, None, Seq.empty)
    }.build.toDataNode

    importProjectData(testProject)
  }

  def testValidJavaSdk: Unit =
    doTestSdk(Some(JdkByVersion("1.8")),
      ProjectJdkTable.getInstance().findJdk(IdeaTestUtil.getMockJdk18.getName),
      LanguageLevel.JDK_1_8)

  def testValidJavaSdkWithDifferentLanguageLevel: Unit =
    doTestSdk(Some(JdkByVersion("1.8")),
      Seq("-source", "1.6"),
      ProjectJdkTable.getInstance().findJdk(IdeaTestUtil.getMockJdk18.getName),
      LanguageLevel.JDK_1_6)

  def testInvalidSdk: Unit =
    doTestSdk(Some(JdkByVersion("20")), defaultJdk, LanguageLevel.JDK_1_7)

  def testAbsentSdk: Unit =
    doTestSdk(None, defaultJdk, LanguageLevel.JDK_1_7)

  def testValidJdkByHome: Unit = {
    val jdk = ProjectJdkTable.getInstance().findJdk(IdeaTestUtil.getMockJdk18.getName)
    doTestSdk(Some(JdkByHome(new File(jdk.getHomePath))), jdk, LanguageLevel.JDK_1_8)
  }

  def testJavacOptions: Unit = {
    val options = Seq(
      "-g:none",
      "-nowarn",
      "-deprecation",
      "-target", "1.8",
      "-Werror"
    )
    importProjectData(generateJavaProject(None, options))

    val compilerConfiguration = CompilerConfiguration.getInstance(getProject)
    assertEquals("1.8", compilerConfiguration.getBytecodeTargetLevel(getModule))
  }

  private def generateScalaProject(scalaVersion: String, scalaLibraryVersion: Option[String], scalacOptions: Seq[String]): DataNode[ProjectData] =
    generateProject(Some(scalaVersion), scalaLibraryVersion, scalacOptions, None, Seq.empty)

  private def generateJavaProject(jdk: Option[Sdk], javacOptions: Seq[String]): DataNode[ProjectData] =
    generateProject(None, None, Seq.empty, jdk, javacOptions)

  private def generateProject(scalaVersion: Option[String], scalaLibraryVersion: Option[String], scalacOptions: Seq[String], jdk: Option[Sdk], javacOptions: Seq[String]): DataNode[ProjectData] =
    new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath
      arbitraryNodes += new SbtProjectNode(Seq.empty, None, Seq.empty, "", getProject.getBasePath)

      val scalaLibrary = scalaLibraryVersion.map { version =>
        new library { name := "org.scala-lang:scala-library:" + version }
      }
      scalaLibrary.foreach(libraries += _)

      modules += new javaModule {
        name := "Module 1"
        moduleFileDirectoryPath := getProject.getBasePath + "/module1"
        externalConfigPath := getProject.getBasePath + "/module1"
        scalaLibrary.foreach(libraryDependencies += _)
        arbitraryNodes += new ModuleExtNode(scalaVersion.map(Version(_)), Seq.empty, scalacOptions, jdk, javacOptions)
      }
    }.build.toDataNode

  private def doTestAndCheckScalaSdk(scalaVersion: String, scalaLibraryVersion: String): Unit = {
    import org.jetbrains.plugins.scala.project._
    importProjectData(generateScalaProject(scalaVersion, Some(scalaLibraryVersion), Seq.empty))
    val isLibrarySetUp = ProjectLibraryTable.getInstance(getProject).getLibraries.filter(_.getName.contains("scala-library")).exists(_.isScalaSdk)
    assertTrue("Scala library is not set up", isLibrarySetUp)
  }

  private def doTestSdk(sdk: Option[Sdk], expectedSdk: projectRoots.Sdk, expectedLanguageLevel: LanguageLevel): Unit =
    doTestSdk(sdk, Seq.empty, expectedSdk, expectedLanguageLevel)

  private def doTestSdk(sdk: Option[Sdk], javacOptions: Seq[String], expectedSdk: projectRoots.Sdk, expectedLanguageLevel: LanguageLevel): Unit = {
    importProjectData(generateJavaProject(sdk, javacOptions))

    val moduleRootManager = ModuleRootManager.getInstance(getModule)
    if (sdk.flatMap(SdkUtils.findProjectSdk).isEmpty) {
      assertTrue(moduleRootManager.isSdkInherited)
    } else {
      assertEquals(expectedSdk, moduleRootManager.getSdk)
      val languageLevelModuleExtension = LanguageLevelModuleExtensionImpl.getInstance(getModule)
      val actualLanguageLevel = languageLevelModuleExtension.getLanguageLevel
      assertEquals(expectedLanguageLevel, actualLanguageLevel)
    }
  }

  private def setUpJdks(): Unit = {
    ApplicationManagerEx.getApplicationEx.runWriteAction(new Runnable {
      def run(): Unit = {
        val projectJdkTable = ProjectJdkTable.getInstance()
        projectJdkTable.getAllJdks.foreach(projectJdkTable.removeJdk)
        projectJdkTable.addJdk(IdeaTestUtil.getMockJdk17)
        projectJdkTable.addJdk(IdeaTestUtil.getMockJdk18)
      }
    })
    // TODO: find a way to create mock Android SDK
  }

  private def defaultJdk: projectRoots.Sdk =
    ProjectJdkTable.getInstance().getAllJdks.head

  override def getModule: Module =
    ModuleManager.getInstance(getProject).findModuleByName("Module 1")
}
