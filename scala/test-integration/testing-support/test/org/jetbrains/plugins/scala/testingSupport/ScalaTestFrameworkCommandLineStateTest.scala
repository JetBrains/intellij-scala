package org.jetbrains.plugins.scala.testingSupport

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.{DefaultJavaProgramRunner, RunManagerImpl, RunnerAndConfigurationSettingsImpl}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.`macro`.MacroManager
import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext, LangDataKeys, PlatformCoreDataKeys}
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.HeavyPlatformTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.extensions.{StringExt, inWriteAction}
import org.jetbrains.plugins.scala.testingSupport.test.CustomTestRunnerBasedStateProvider.TestFrameworkRunnerInfo
import org.jetbrains.plugins.scala.testingSupport.test.ScalaTestFrameworkCommandLineState
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.{ScalaTestConfigurationType, ScalaTestRunConfiguration}
import org.jetbrains.sbt.project.data.ModuleNode
import org.jetbrains.sbt.project.data.service.ExternalSystemDataDsl._
import org.jetbrains.sbt.project.data.service.ProjectDataServiceTestCase
import org.junit.Assert.{assertEquals, assertNotNull}

import java.io.File
import java.net.URI
import java.util
import scala.jdk.CollectionConverters.{CollectionHasAsScala, MapHasAsJava}

class ScalaTestFrameworkCommandLineStateTest extends HeavyPlatformTestCase {

  private lazy val jdk = SmartJDKLoader.getOrCreateJDK()

  override def getTestProjectJdk: Sdk = jdk

  override def tearDown(): Unit = {
    inWriteAction {
      ProjectJdkTable.getInstance().removeJdk(jdk)
    }
    super.tearDown()
  }

  private val DummyExecutableName = "dummy-executable-name"
  private val DummyRunnerClass = "DummyRunnerClass"
  private val DummyTestClass = "DummyTestClass"
  private val DummyTestName = "dummyTestName"

  def testExpandPathVarsMacroAndEnvVars_HealthCheck(): Unit = {
    val project = getProject
    val myCustomModule = setupTestProjectAndModule(project)

    val javaOptions: String =
      """-javaOptionParam1
        |-macro_value=$ProjectName$
        |-module_dir_deprecated=$MODULE_DIR$
        |-project_path=$PROJECT_DIR$
        |-D$EnvKey1$
        |""".stripMargin.trim
    val programArguments =
      """-programParam1
        |-macro_value=$ProjectName$
        |-module_dir_deprecated=$MODULE_DIR$
        |-project_path=$PROJECT_DIR$
        |""".stripMargin.trim
    val workingDirectory = "/my/working/directory"

    val envs = Map(
      "EnvKey1" -> "EnvValue1"
    ).asJava

    val (actualCommandLine, actualWorkingDir) =
      getConfigurationCommandLineAndWorkingDirectory(myCustomModule, javaOptions, programArguments, workingDirectory, envs)

    assertWorkingDirectory(s"/my/working/directory", actualWorkingDir)
    assertCommandLine(
      s"""$DummyExecutableName
         |
         |-javaOptionParam1
         |-macro_value=${project.getName}
         |-module_dir_deprecated=${project.getBasePath}/myModuleName1
         |-project_path=${project.getBasePath}
         |-DEnvValue1
         |
         |-Dfile.encoding=UTF-8
         |
         |$DummyRunnerClass
         |
         |-s
         |$DummyTestClass
         |-testName
         |$DummyTestName
         |-showProgressMessages
         |true
         |
         |-programParam1
         |-macro_value=${project.getName}
         |-module_dir_deprecated=${project.getBasePath}/myModuleName1
         |-project_path=${project.getBasePath}
         |""".stripMargin.withNormalizedSeparator.replaceAll("\n\n+", "\n").trim,
      actualCommandLine
    )
  }

  // note: for now MODULE_WORKING_DIR only works when it's the only macro: IDEA-280681
  def testExpandWorkingDirectory_ModuleWorkingDir(): Unit = {
    val myCustomModule = setupTestProjectAndModule(getProject)
    val workingDirectory = "$MODULE_WORKING_DIR$"
    val (_, actualWorkingDir) =
      getConfigurationCommandLineAndWorkingDirectory(myCustomModule, "", "", workingDirectory, new util.HashMap())
    assertWorkingDirectory(getProject.getBasePath + "/myModuleName1", actualWorkingDir)
  }

  def testExpandWorkingDirectory_ProjectDir(): Unit = {
    val myCustomModule = setupTestProjectAndModule(getProject)
    val workingDirectory = "$PROJECT_DIR$/some/relative/path"
    val (_, actualWorkingDir) =
      getConfigurationCommandLineAndWorkingDirectory(myCustomModule, "", "", workingDirectory, new util.HashMap())
    assertWorkingDirectory(getProject.getBasePath + "/some/relative/path", actualWorkingDir)
  }

  def testWorkingDirectory_Empty(): Unit = {
    ensureProjectRootExists()

    val myCustomModule = setupTestProjectAndModule(getProject)
    val workingDirectory = ""

    val (_, actualWorkingDir) =
      getConfigurationCommandLineAndWorkingDirectory(myCustomModule, "", "", workingDirectory, new util.HashMap())
    assertWorkingDirectory(getProject.getBasePath, actualWorkingDir)
  }

  def testWorkingDirectory_RelativePath(): Unit = {
    ensureProjectRootExists() // ensure project folder exists

    val myCustomModule = setupTestProjectAndModule(getProject)
    val workingDirectory = "some/relative/path"

    val (_, actualWorkingDir) =
      getConfigurationCommandLineAndWorkingDirectory(myCustomModule, "", "", workingDirectory, new util.HashMap())
    assertWorkingDirectory(getProject.getBasePath + "/some/relative/path", actualWorkingDir)
  }

  def testWorkingDirectory_RelativePath_WithDot(): Unit = {
    ensureProjectRootExists() // ensure project folder exists

    val myCustomModule = setupTestProjectAndModule(getProject)
    val workingDirectory = "./some/relative/path"

    val (_, actualWorkingDir) =
      getConfigurationCommandLineAndWorkingDirectory(myCustomModule, "", "", workingDirectory, new util.HashMap())
    assertWorkingDirectory(getProject.getBasePath + "/./some/relative/path", actualWorkingDir)
  }

  private def getConfigurationCommandLineAndWorkingDirectory(
    module: Module,
    javaOptions: String,
    programArguments: String,
    workingDirectory: String,
    envs: util.Map[String, String],
  ): (String, String) = {
    val project = module.getProject
    val configuration = new ScalaTestRunConfiguration(project, ScalaTestConfigurationType().confFactory, "test-conf-name")
    configuration.setModule(module)
    configuration.testConfigurationData.setJavaOptions(javaOptions)
    configuration.testConfigurationData.setProgramParameters(programArguments)
    configuration.testConfigurationData.setWorkingDirectory(workingDirectory)
    configuration.testConfigurationData.setEnvs(envs)

    val state = buildCommandLineState(project, configuration)
    val javaParameters = state.getJavaParameters
    assertNotNull(javaParameters)

    javaParameters.getClassPath.clear() // do not test "-classpath" in current test cause it contains a lot of mess
    val actualCommandLineString =
      javaParameters.toCommandLine.getCommandLineList(DummyExecutableName).asScala.mkString("\n")

    (actualCommandLineString, javaParameters.getWorkingDirectory)
  }

  private def assertWorkingDirectory(expectedModuleWorkingDir: String, actualWorkingDir: String): Unit =
    assertEquals(
      "Working directory doesn't match",
      expectedModuleWorkingDir.replace("\\", "/"),
      if (actualWorkingDir!= null) actualWorkingDir.replace("\\", "/") else null
    )

  private def assertCommandLine(expectedCommandLineString: String, actualCommandLineString: String): Unit =
    assertEquals(
      "Command line doesn't match",
      expectedCommandLineString,
      actualCommandLineString
    )

  private def setupTestProjectAndModule(project: Project): Module = {
    val projectBasePath: String = project.getBasePath

    val myCustomModuleName = "Module 1"

    val projectStructure = new project {
      name := project.getName
      ideDirectoryPath := projectBasePath
      linkedProjectPath := projectBasePath

      modules ++= Seq(new javaModule {
        val uri: URI = new File(projectBasePath).toURI
        val id: String = ModuleNode.combinedId(myCustomModuleName, Option(uri))

        projectId := id
        projectURI := uri
        name := myCustomModuleName
        moduleFileDirectoryPath := s"$projectBasePath/myModuleName1"
        externalConfigPath := s"$projectBasePath/myModuleName1"
      })
    }.build.toDataNode

    ProjectDataServiceTestCase.importProjectData(projectStructure, project)
    setUpJdk() // modules are updated so we need to again setup their jdk

    ModuleManager.getInstance(project).findModuleByName(myCustomModuleName)
  }

  private def ensureProjectRootExists(): Unit = {
    val file = new File(getProject.getBasePath)
    file.mkdirs()
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
  }

  private def buildCommandLineState(project: Project, configuration: ScalaTestRunConfiguration): ScalaTestFrameworkCommandLineState = {
    val env = buildDefaultExecutionEnvironment(project, configuration)
    val failedTests: Option[Seq[(String, String)]] = Some(Seq(DummyTestClass -> DummyTestName))
    val runnerInfo = TestFrameworkRunnerInfo(DummyRunnerClass)
    new ScalaTestFrameworkCommandLineState(configuration, env, failedTests, runnerInfo)
  }

  private def buildDefaultExecutionEnvironment(project: Project, configuration: ScalaTestRunConfiguration) = {
    val executor = new DefaultRunExecutor
    val programRunner = new DefaultJavaProgramRunner
    val settings = new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project), configuration)
    new ExecutionEnvironment(executor, programRunner, settings, project)
  }
}
