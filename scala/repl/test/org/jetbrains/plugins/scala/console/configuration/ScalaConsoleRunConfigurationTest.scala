//noinspection ApiStatus
package org.jetbrains.plugins.scala.console.configuration

import com.intellij.execution.configurations.{JavaCommandLineState, JavaParameters}
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.{DefaultJavaProgramRunner, RunManagerImpl, RunnerAndConfigurationSettingsImpl}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.{ModuleRootModificationUtil, ProjectRootManager}
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.{FixturesKt, TestFixture}
import com.intellij.util.lang.JavaVersion
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.libraryLoaders.MockScalaSDKLoader
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertNotNull, assertTrue}
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.{ListHasAsScala, MapHasAsJava}

@TestApplication
class ScalaConsoleRunConfigurationTest:

  /**
   * These fixtures must be defined as class members in order to be initialized and torn down by the
   * `@TestApplication`.
   */
  private val pf: TestFixture[Project] = FixturesKt.projectFixture()
  private val mf: TestFixture[Module] = FixturesKt.moduleFixture(pf)

  /**
   * Sets up a mock JDK as the project and module JDK and sets up a Scala SDK for the provided Scala version.
   * This specific test setup does not require a corresponding teardown, as everything is handled by the teardown
   * of the module and project fixtures.
   */
  private def withScalaSdk(scalaVersion: ScalaVersion)(test: (Project, Module) ?=> Unit): Unit =
    val project = pf.get()
    val module = mf.get()

    val jdk = IdeaTestUtil.getMockJdk(JavaVersion.compose(17))
    val jdkTable = ProjectJdkTable.getInstance()

    WriteAction.runAndWait: () =>
      if !jdkTable.getAllJdks.contains(jdk) then
        jdkTable.addJdk(jdk, project)
      ProjectRootManager.getInstance(project).setProjectSdk(jdk)
      ModuleManager.getInstance(project).getModules.foreach(ModuleRootModificationUtil.setModuleSdk(_, jdk))

    MockScalaSDKLoader().init(using module, scalaVersion)
    test(using project, module)
  end withScalaSdk

  // --- Main class ---

  @Test
  def mainClass_Scala2(): Unit =
    withScalaSdk(ScalaVersion.Latest.Scala_2_13):
      val params = buildJavaParameters(createConfiguration)
      assertEquals("scala.tools.nsc.MainGenericRunner", params.getMainClass)

  @Test
  def mainClass_Scala3(): Unit =
    withScalaSdk(ScalaVersion.Latest.Scala_3):
      val params = buildJavaParameters(createConfiguration)
      assertEquals("dotty.tools.repl.Main", params.getMainClass)

  // --- JLine handling ---

  @Test
  def jLine_Scala2_12_UsesXnojline(): Unit =
    withScalaSdk(ScalaVersion.Latest.Scala_2_12):
      val params = buildJavaParameters(createConfiguration)
      val programParams = params.getProgramParametersList.getList.asScala
      assertTrue(programParams.contains("-Xnojline"), "Expected -Xnojline for Scala 2.12")

  @Test
  def jLine_Scala2_13_2_to_2_13_14_UsesXjlineOff(): Unit =
    // Scala 2.13.5 is between 2.13.2 and 2.13.14
    withScalaSdk("2.13.5".scalaVersion):
      val params = buildJavaParameters(createConfiguration)
      val programParams = params.getProgramParametersList.getList.asScala
      assertTrue(programParams.contains("-Xjline:off"), "Expected -Xjline:off for Scala 2.13.5")

  @Test
  def jLine_Scala2_13_14Plus_UsesXnojline(): Unit =
    withScalaSdk("2.13.15".scalaVersion):
      val params = buildJavaParameters(createConfiguration)
      val programParams = params.getProgramParametersList.getList.asScala
      assertTrue(programParams.contains("-Xnojline"), "Expected -Xnojline for Scala 2.13.15")

  @Test
  def jLine_Scala3_SetsTermDumb(): Unit =
    withScalaSdk(ScalaVersion.Latest.Scala_3):
      val params = buildJavaParameters(createConfiguration)
      assertEquals("dumb", params.getEnv.get("TERM"), "Expected TERM=dumb for Scala 3")
      val programParams = params.getProgramParametersList.getList.asScala
      assertFalse(programParams.contains("-Xnojline"), "Should not contain -Xnojline for Scala 3")
      assertFalse(programParams.contains("-Xjline:off"), "Should not contain -Xjline:off for Scala 3")

  // --- Console args ---

  @Test
  def consoleArgs_DefaultUsesJavaCp(): Unit =
    withScalaSdk(ScalaVersion.Latest.Scala_2_13):
      val params = buildJavaParameters(createConfiguration)
      val programParams = params.getProgramParametersList.getList.asScala
      assertTrue(programParams.contains("-usejavacp"), "Expected -usejavacp by default")

  @Test
  def consoleArgs_CustomArgsPreserved(): Unit =
    withScalaSdk(ScalaVersion.Latest.Scala_2_13):
      val config = createConfiguration
      config.consoleArgs = "-usejavacp -deprecation"
      val params = buildJavaParameters(config)
      val programParams = params.getProgramParametersList.getList.asScala
      assertTrue(programParams.contains("-usejavacp"), "Expected -usejavacp")
      assertTrue(programParams.contains("-deprecation"), "Expected -deprecation")

    // --- Java options, working directory, environment variables ---

  @Test
  def javaOptions(): Unit =
    withScalaSdk(ScalaVersion.Latest.Scala_2_13):
      val config = createConfiguration
      config.javaOptions = "-Xmx512m -Dfoo=bar"
      val params = buildJavaParameters(config)
      val vmParams = params.getVMParametersList.getList.asScala
      assertTrue(vmParams.contains("-Xmx512m"), "Expected -Xmx512m in VM params")
      assertTrue(vmParams.contains("-Dfoo=bar"), "Expected -Dfoo=bar in VM params")

  @Test
  def workingDirectory(): Unit =
    withScalaSdk(ScalaVersion.Latest.Scala_2_13):
      val config = createConfiguration
      config.workingDirectory = "/custom/working/dir"
      val params = buildJavaParameters(config)
      assertEquals("/custom/working/dir", params.getWorkingDirectory)

  @Test
  def environmentVariables(): Unit =
    withScalaSdk(ScalaVersion.Latest.Scala_2_13):
      val config = createConfiguration
      config.environmentVariables = Map("MY_VAR" -> "my_value", "FOO" -> "bar").asJava
      val params = buildJavaParameters(config)
      assertEquals("my_value", params.getEnv.get("MY_VAR"))
      assertEquals("bar", params.getEnv.get("FOO"))

  private def createConfiguration(using project: Project, module: Module): ScalaConsoleRunConfiguration =
    val configType = ScalaConsoleConfigurationType()
    val factory = configType.getConfigurationFactories().head
    val config = ScalaConsoleRunConfiguration(project, factory, "test-console")
    config.setModule(module)
    config

  private def buildJavaParameters(config: ScalaConsoleRunConfiguration)(using project: Project): JavaParameters =
    val executor = DefaultRunExecutor()
    val runner = DefaultJavaProgramRunner()
    val settings = RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project), config)
    val env = ExecutionEnvironment(executor, runner, settings, project)

    val state = config.getState(executor, env)
    assertNotNull(state, "RunProfileState should not be null")

    val params = state.asInstanceOf[JavaCommandLineState].getJavaParameters
    assertNotNull(params, "JavaParameters should not be null")
    params
  end buildJavaParameters

  extension (s: String)
    def scalaVersion: ScalaVersion =
      ScalaVersion.fromString(s).getOrElse(throw IllegalArgumentException(s"Could not parse Scala version: $s"))
