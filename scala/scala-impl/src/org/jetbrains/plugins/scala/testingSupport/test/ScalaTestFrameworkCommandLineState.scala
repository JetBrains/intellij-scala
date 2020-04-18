package org.jetbrains.plugins.scala.testingSupport.test

import java.io.{File, FileOutputStream, IOException, PrintStream}
import java.{util => ju}

import com.intellij.execution.configurations.{JavaCommandLineState, JavaParameters, ParametersList, RunConfigurationBase}
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.testDiscovery.JavaAutoRunManager
import com.intellij.execution.testframework.autotest.{AbstractAutoTestManager, ToggleAutoTestAction}
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.{DefaultExecutionResult, ExecutionException, ExecutionResult, Executor, JavaRunConfigurationExtensionManager, RunConfigurationExtension}
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JdkUtil, ProjectJdkTable, Sdk}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.extensions.{IteratorExt, ObjectExt, using}
import org.jetbrains.plugins.scala.project.{PathsListExt, ProjectExt}
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}
import org.jetbrains.plugins.scala.testingSupport.locationProvider.ScalaTestLocationProvider
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.PropertiesExtension
import org.jetbrains.plugins.scala.testingSupport.test.ScalaTestFrameworkCommandLineState._
import org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.SearchForTest
import org.jetbrains.plugins.scala.testingSupport.test.actions.ScalaRerunFailedTestsAction
import org.jetbrains.plugins.scala.testingSupport.test.sbt.{ReportingSbtTestEventHandler, SbtProcessHandlerWrapper, SbtShellTestsRunner, SbtTestRunningSupport}
import org.jetbrains.plugins.scala.testingSupport.test.testdata.TestConfigurationData
import org.jetbrains.sbt.shell.SbtProcessManager

import scala.collection.JavaConverters._

class ScalaTestFrameworkCommandLineState(
  configuration: AbstractTestRunConfiguration,
  env: ExecutionEnvironment,
  testConfigurationData: TestConfigurationData,
  runnerInfo: TestFrameworkRunnerInfo,
  sbtSupport: SbtTestRunningSupport
)(implicit project: Project, module: Module)
  extends JavaCommandLineState(env)
    with AbstractTestRunConfiguration.TestCommandLinePatcher {

  override val getClasses: Seq[String] = testConfigurationData.getTestMap.keys.toSeq

  private def suitesToTestsMap: Map[String, Set[String]] = {
    val failedTests = getFailedTests.groupBy(_._1).map { case (aClass, tests) => (aClass, tests.map(_._2).toSet) }.filter(_._2.nonEmpty)
    if (failedTests.nonEmpty) {
      failedTests
    } else {
      testConfigurationData.getTestMap
    }
  }

  override def createJavaParameters(): JavaParameters = {
    val params = new JavaParameters()

    params.setCharset(null)

    val envs = new ju.HashMap[String, String](testConfigurationData.envs)
    params.setEnv(envs)

    val vmParams = {
      val params0 = testConfigurationData.getJavaOptions
      val params1 = expandPath(params0)
      expandEnvs(params1, envs.asScala)
    }
    params.getVMParametersList.addParametersString(vmParams)

    // UNCOMMENT TO DEBUG:
    //params.getVMParametersList.addParametersString("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5007")

    val workingDir = testConfigurationData.getWorkingDirectory
    params.setWorkingDirectory(expandPath(workingDir))

    params.getClassPath.addRunners()
    params.setMainClass(runnerInfo.runnerClass)

    // hack fix for SCL-12564
    ManagingFS.getInstance match {
      case fs: PersistentFSImpl => fs.incStructuralModificationCount()
      case _                    =>
    }

    val maybeCustomSdk = for {
      path <- testConfigurationData.jrePath.toOption
      jdk  <- ProjectJdkTable.getInstance().findJdk(path).toOption
    } yield jdk

    testConfigurationData.searchTest match {
      case SearchForTest.IN_WHOLE_PROJECT => // TODO: not the best to match against this
        def anyJdk: Option[Sdk] = {
          val modules = project.modules.iterator
          modules.map(JavaParameters.getValidJdkToRunModule(_, false)).headOption
        }
        val sdk = maybeCustomSdk.orElse(anyJdk)
        params.configureByProject(project, JavaParameters.JDK_AND_CLASSES_AND_TESTS, sdk.orNull)
      case _ =>
        val sdk = maybeCustomSdk.getOrElse(JavaParameters.getValidJdkToRunModule(module, false))
        params.configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS, sdk)
    }

    val programParameters = buildProgramParameters(suitesToTestsMap)
    if (JdkUtil.useDynamicClasspath(project)) {
      val fileWithParams = prepareDynamicClasspathFile(programParameters)
      params.getProgramParametersList.add("@" + fileWithParams.getPath)
    } else {
      programParameters.foreach(params.getProgramParametersList.add)
    }

    for (ext <- RunConfigurationExtension.EP_NAME.getExtensionList.asScala)
      ext.updateJavaParameters(configuration, params, getRunnerSettings)

    params
  }

  private def buildProgramParameters(suitesToTests: Map[String, Set[String]]): Seq[String] = {
    val classesAndTests = buildClassesAndTestsParameters(suitesToTests)
    val runner = runnerInfo.reporterClass.toSeq.flatMap(Seq("-C", _))
    val progress = Seq("-showProgressMessages", testConfigurationData.showProgressMessages.toString)
    val other = ParametersList.parse(testConfigurationData.getTestArgs)
    classesAndTests ++ runner ++ progress ++ other
  }

  private def buildClassesAndTestsParameters(suitesToTests: Map[String, Set[String]]): Seq[String] = {
    val classKey = "-s"
    val testNameKey = "-testName"

    suitesToTests.flatMap { case (className, tests) =>
      val classParams = Seq(classKey, sanitize(className))
      val testsParams = tests.toSeq.filter(StringUtils.isNotBlank).flatMap(Seq(testNameKey, _))
      classParams ++ testsParams
    }.toSeq
  }

  override def execute(executor: Executor, runner: ProgramRunner[_]): ExecutionResult = {
    val useSbt = testConfigurationData.useSbt
    val useUiWithSbt = testConfigurationData.useUiWithSbt

    val processHandler = if (useSbt) { // TODO: only if supported
      sbtShellProcess(project)
    } else {
      startProcess()
    }

    if (getConfiguration == null)
      setConfiguration(configuration)
    val config = getConfiguration // shouldn't it be used everywhere below instead of thisConfiguration?

    attachExtensionsToProcess(processHandler)

    val useSimplifiedConsoleView = useSbt && !useUiWithSbt
    val consoleView = if (useSimplifiedConsoleView) {
      new ConsoleViewImpl(project, true)
    } else {
      //noinspection TypeAnnotation
      val consoleProperties = new SMTRunnerConsoleProperties(configuration, "Scala", executor) with PropertiesExtension {
        override def getTestLocator = new ScalaTestLocationProvider
        override def getRunConfigurationBase: RunConfigurationBase[_] = config
      }
      consoleProperties.setIdBasedTestTree(true)
      SMTestRunnerConnectionUtil.createConsole("Scala", consoleProperties)
    }
    consoleView.attachToProcess(processHandler)

    val executionResult = createExecutionResult(consoleView, processHandler)

    // uncomment for debugging
    //processHandler.addProcessListener(new ProcessAdapter {
    //  override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit =
    //    print(s"[$outputType] ${event.getText}")
    //})

    if (useSbt) {
      Stats.trigger(FeatureKey.sbtShellTestRunConfig)
      val future = SbtShellTestsRunner.runTestsInSbtShell(
        sbtSupport,
        module,
        suitesToTestsMap,
        new ReportingSbtTestEventHandler((message, key) => {
          processHandler.notifyTextAvailable(message, key)
        }),
        useUiWithSbt
      )
      future.onComplete(_ => processHandler.destroyProcess())(sbtSupport.executionContext)
    }

    executionResult
  }

  private def attachExtensionsToProcess(processHandler: ProcessHandler): Unit = {
    val runnerSettings = getRunnerSettings
    JavaRunConfigurationExtensionManager.getInstance
      .attachExtensionsToProcess(configuration, processHandler, runnerSettings)
  }

  private def sbtShellProcess(project: Project): ProcessHandler = {
    //use a process running sbt
    val sbtProcessManager = SbtProcessManager.forProject(project)
    //make sure the process is initialized
    val shellRunner = sbtProcessManager.acquireShellRunner()
    SbtProcessHandlerWrapper(shellRunner.createProcessHandler)
  }
}

object ScalaTestFrameworkCommandLineState {

  private def expandPath(text: String)(implicit project: Project, module: Module): String =
    Some(text)
      .map(PathMacroManager.getInstance(project).expandPath)
      .map(PathMacroManager.getInstance(module).expandPath)
      .get

  private def expandEnvs(text: String, envs: scala.collection.Map[String, String]) =
    envs.foldLeft(text) { case (text, (key, value)) =>
      StringUtil.replace(text, "$" + key + "$", value, false)
    }

  private def prepareDynamicClasspathFile(
    programParameters: Seq[String]
  ): File = try {
    val fileWithParams: File = File.createTempFile("abstracttest", ".tmp")
    using(new PrintStream(new FileOutputStream(fileWithParams))) { printer =>
      programParameters.foreach(printer.println)
    }
    fileWithParams
  } catch {
    case e: IOException =>
      throw new ExecutionException("Failed to create dynamic classpath file with command-line args.", e)
  }

  private def createExecutionResult(consoleView: ConsoleView, processHandler: ProcessHandler): DefaultExecutionResult = {
    val result = new DefaultExecutionResult(consoleView, processHandler)
    val restartActions = createRestartActions(consoleView).toSeq.flatten
    result.setRestartActions(restartActions: _*)
    result
  }

  private def createRestartActions(consoleView: ConsoleView) =
    consoleView match {
      case testConsole: BaseTestsOutputConsoleView =>
        val rerunFailedTestsAction = {
          val action = new ScalaRerunFailedTestsAction(testConsole)
          action.init(testConsole.getProperties)
          action.setModelProvider(() => testConsole.asInstanceOf[SMTRunnerConsoleView].getResultsViewer)
          action
        }
        val toggleAutoTestAction   = new ToggleAutoTestAction() {
          override def isDelayApplicable: Boolean = false
          override def getAutoTestManager(project: Project): AbstractAutoTestManager = JavaAutoRunManager.getInstance(project)
        }
        Some(Seq(rerunFailedTestsAction, toggleAutoTestAction))
      case _ =>
        None
    }

  // ScalaTest does not understand backticks in class/package qualified names, it will fail to run
  private def sanitize(qualifiedName: String): String = qualifiedName.replace("`", "")
}
