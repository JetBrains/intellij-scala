package org.jetbrains.sbt.project.data.service

import com.intellij.compiler.CompilerConfiguration
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.module.{LanguageLevelUtil, ModuleManager}
import com.intellij.openapi.projectRoots
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.{IdeaTestUtil, UsefulTestCase}
import org.jetbrains.plugins.scala.compiler.data.DebuggingInfoLevel
import org.jetbrains.plugins.scala.project.external.{JdkByHome, JdkByName, SdkReference, SdkUtils}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.sbt.project.data._
import org.junit.Assert._

import java.io.File
import scala.jdk.CollectionConverters._

class SbtModuleExtDataServiceTest extends SbtModuleDataServiceTestCase {

  import ExternalSystemDataDsl._

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

  private def generateJavaProject(sdk: Option[SdkReference], moduleJavacOptions: Seq[String]): DataNode[ProjectData] =
    generateProject(None, None, Seq.empty, sdk, moduleJavacOptions)

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

  private def defaultJdk: projectRoots.Sdk =
    ProjectJdkTable.getInstance().getAllJdks.head

}
