package org.jetbrains.sbt.project

import java.io.File

import com.android.sdklib.repository.local.LocalSdk
import com.intellij.compiler.{CompilerConfigurationImpl, CompilerConfiguration}
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.module.{ModuleUtil, ModuleManager}
import com.intellij.openapi.projectRoots.{Sdk, JavaSdk, ProjectJdkTable}
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.android.sdk.{AndroidSdkData, AndroidPlatform, AndroidSdkAdditionalData, AndroidSdkType}
import org.jetbrains.plugins.scala.project.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.sbt.UsefulTestCaseHelper
import org.jetbrains.sbt.project.data.{ScalaProjectData, ScalaProjectNode}

import ExternalSystemDsl._
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.project.sources.SharedSourcesModuleType
import org.jetbrains.sbt.settings.SbtSystemSettings
import scala.collection.JavaConverters._

/**
 * @author Nikolay Obedin
 * @since 6/15/15.
 */
class SbtProjectDataServiceTest extends ProjectDataServiceTestCase with UsefulTestCaseHelper {

  override def setUp(): Unit = {
    super.setUp()
    setUpJdks()
  }


  private def setUpJdks(): Unit = {
    val projectJdkTable = ProjectJdkTable.getInstance()
    val javaSdk = IdeaTestUtil.getMockJdk18
    ApplicationManagerEx.getApplicationEx.runWriteAction(new Runnable {
      def run(): Unit = projectJdkTable.addJdk(javaSdk)
    })
    // TODO: find a way to create mock Android SDK
  }

  private def generateProject(basePackages: Seq[String], jdk: Option[ScalaProjectData.Sdk], javacOptions: Seq[String], sbtVersion: String): DataNode[ProjectData] =
    new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath

      arbitraryNodes += new ScalaProjectNode(basePackages, jdk, javacOptions, sbtVersion, getProject.getBasePath)
    }.build.toDataNode

  private def doTestBasePackages(basePackages: Seq[String]): Unit = {
    importProjectData(generateProject(basePackages, None, Seq.empty, ""))
    assert(ScalaProjectSettings.getInstance(getProject).getBasePackages.asScala == basePackages)
  }

  def testEmptyBasePackages: Unit =
    doTestBasePackages(Seq.empty)
  def testNonEmptyBasePackages: Unit =
    doTestBasePackages(Seq("com.test1.base", "com.test2.base"))

  private def existingOrDefaultSdk: Sdk =
    Option(ProjectRootManager.getInstance(getProject).getProjectSdk)
      .getOrElse(ProjectJdkTable.getInstance().getAllJdks.head)

  private def doTestSdk(sdk: Option[ScalaProjectData.Sdk], expectedSdk: Sdk): Unit = {
    importProjectData(generateProject(Seq.empty, sdk, Seq.empty, ""))
    assert(ProjectRootManager.getInstance(getProject).getProjectSdk == expectedSdk)
  }

  def testValidJavaSdk: Unit =
    doTestSdk(Some(ScalaProjectData.Jdk("1.8")), ProjectJdkTable.getInstance().findJdk(IdeaTestUtil.getMockJdk18.getName))
  def testInvalidSdk: Unit =
    doTestSdk(Some(ScalaProjectData.Jdk("20")), existingOrDefaultSdk)
  def testAbsentSdk: Unit =
    doTestSdk(None, existingOrDefaultSdk)

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
    projectSettings.setExternalProjectPath(getProject.getBasePath)
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

      arbitraryNodes += new ScalaProjectNode(Seq.empty, None, Seq.empty, "", getProject.getBasePath)
    }.build.toDataNode

    importProjectData(testProject)
    assert(ScalaCompilerConfiguration.instanceIn(getProject).incrementalityType == IncrementalityType.SBT)
  }
}
