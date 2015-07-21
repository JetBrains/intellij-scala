package org.jetbrains.sbt.project

import java.io.File

import com.android.sdklib.repository.local.LocalSdk
import com.intellij.compiler.{CompilerConfigurationImpl, CompilerConfiguration}
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.{ModuleUtil, ModuleManager}
import com.intellij.openapi.projectRoots.{Sdk, JavaSdk, ProjectJdkTable}
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.roots.{LanguageLevelProjectExtension, ProjectRootManager}
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.android.sdk.{AndroidSdkData, AndroidPlatform, AndroidSdkAdditionalData, AndroidSdkType}
import org.jetbrains.plugins.scala.project.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.sbt.UsefulTestCaseHelper
import org.jetbrains.sbt.project.data.{SbtProjectData, SbtProjectNode}

import ExternalSystemDsl._
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.project.sources.SharedSourcesModuleType
import org.jetbrains.sbt.settings.SbtSystemSettings
import scala.collection.JavaConverters._

/**
 * @author Nikolay Obedin
 * @since 6/15/15.
 */
class SbtProjectDataServiceTest extends ProjectDataServiceTestCase {

  override def setUp(): Unit = {
    super.setUp()
    setUpJdks()
  }

  def testEmptyBasePackages: Unit =
    doTestBasePackages(Seq.empty)

  def testNonEmptyBasePackages: Unit =
    doTestBasePackages(Seq("com.test1.base", "com.test2.base"))

  def testValidJavaSdk: Unit =
    doTestSdk(Some(data.JdkByVersion("1.8")),
      ProjectJdkTable.getInstance().findJdk(IdeaTestUtil.getMockJdk18.getName),
      LanguageLevel.JDK_1_8)

  def testValidJavaSdkWithDifferentLanguageLevel: Unit =
    doTestSdk(Some(data.JdkByVersion("1.8")),
      Seq("-source", "1.6"),
      ProjectJdkTable.getInstance().findJdk(IdeaTestUtil.getMockJdk18.getName),
      LanguageLevel.JDK_1_6)

  def testInvalidSdk: Unit =
    doTestSdk(Some(data.JdkByVersion("20")), defaultJdk, LanguageLevel.JDK_1_7)

  def testAbsentSdk: Unit =
    doTestSdk(None, defaultJdk, LanguageLevel.JDK_1_7)

  def testJavacOptions: Unit = {
    val options = Seq(
      "-g:none",
      "-nowarn",
      "-deprecation",
      "-target", "1.8",
      "-Werror"
    )
    importProjectData(generateProject(Seq.empty, None, options, ""))

    val compilerOptions = JavacConfiguration.getOptions(getProject, classOf[JavacConfiguration])
    assert(!compilerOptions.DEBUGGING_INFO)
    assert(compilerOptions.GENERATE_NO_WARNINGS)
    assert(compilerOptions.DEPRECATION)
    assert(compilerOptions.ADDITIONAL_OPTIONS_STRING == "-Werror")

    val compilerConfiguration = CompilerConfiguration.getInstance(getProject)
    assert(compilerConfiguration.getProjectBytecodeTarget == "1.8")
  }

  def testSbtVersion: Unit = {
    val projectSettings = SbtProjectSettings.default
    projectSettings.setExternalProjectPath(ExternalSystemApiUtil.normalizePath(getProject.getBasePath))
    SbtSystemSettings.getInstance(getProject).linkProject(projectSettings)

    val expectedVersion = "0.13.8"
    importProjectData(generateProject(Seq.empty, None, Seq.empty, expectedVersion))
    val actualVersion = SbtSystemSettings.getInstance(getProject).getLinkedProjectSettings(getProject.getBasePath).sbtVersion
    assert(expectedVersion == actualVersion)
  }

  def testIncrementalityTypeForSharedModules: Unit = {
    val testProject = new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath

      modules += new module {
        val typeId = SharedSourcesModuleType.instance.getId
        name := "Module 1"
        moduleFileDirectoryPath := getProject.getBasePath + "/module1"
        externalConfigPath := getProject.getBasePath + "/module1"
      }

      arbitraryNodes += new SbtProjectNode(Seq.empty, None, Seq.empty, "", getProject.getBasePath)
    }.build.toDataNode

    importProjectData(testProject)
    assert(ScalaCompilerConfiguration.instanceIn(getProject).incrementalityType == IncrementalityType.SBT)
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

  private def generateProject(basePackages: Seq[String], jdk: Option[data.Sdk], javacOptions: Seq[String], sbtVersion: String): DataNode[ProjectData] =
    new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath

      arbitraryNodes += new SbtProjectNode(basePackages, jdk, javacOptions, sbtVersion, getProject.getBasePath)
    }.build.toDataNode

  private def doTestBasePackages(basePackages: Seq[String]): Unit = {
    importProjectData(generateProject(basePackages, None, Seq.empty, ""))
    assert(ScalaProjectSettings.getInstance(getProject).getBasePackages.asScala == basePackages)
  }

  private def defaultJdk: Sdk =
    ProjectJdkTable.getInstance().getAllJdks.head

  private def doTestSdk(sdk: Option[data.Sdk], expectedSdk: Sdk, expectedLanguageLevel: LanguageLevel): Unit =
    doTestSdk(sdk, Seq.empty, expectedSdk, expectedLanguageLevel)

  private def doTestSdk(sdk: Option[data.Sdk], javacOptions: Seq[String], expectedSdk: Sdk, expectedLanguageLevel: LanguageLevel): Unit = {
    importProjectData(generateProject(Seq.empty, sdk, javacOptions, ""))
    assert(ProjectRootManager.getInstance(getProject).getProjectSdk == expectedSdk)
    val actualLanguageLevel = LanguageLevelProjectExtension.getInstance(getProject).getLanguageLevel
    assert(actualLanguageLevel == expectedLanguageLevel)
  }
}
