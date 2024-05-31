package org.jetbrains.plugins.scala.testingSupport.test

import com.intellij.execution.configurations.{JavaCommandLineState, JavaParameters, ParametersList}
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView
import com.intellij.execution.{CommonProgramRunConfigurationParameters, ExecutionResult, Executor, JavaRunConfigurationExtensionManager, ShortenCommandLine}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import com.intellij.util.EnvironmentUtil
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.{ModuleExt, PathsListExt, ProjectExt}
import org.jetbrains.plugins.scala.testingSupport.test.CustomTestRunnerBasedStateProvider.TestFrameworkRunnerInfo
import org.jetbrains.plugins.scala.testingSupport.test.ScalaTestFrameworkCommandLineState._
import org.jetbrains.plugins.scala.testingSupport.test.exceptions.executionException
import org.jetbrains.plugins.scala.testingSupport.test.utils.{JavaParametersModified, RawProcessOutputDebugLogger}

import java.io.{File, IOException, PrintStream}
import java.nio.charset.StandardCharsets
import java.{util => ju}
import scala.jdk.CollectionConverters._
import scala.util.Using

/**
 * For ScalaTest, Spec2, uTest
 *
 * @param failedTests if defined, the list of test classes and methods to run
 *                    is taken from it instead of from test data
 */
@ApiStatus.Internal
class ScalaTestFrameworkCommandLineState(
  override val configuration: AbstractTestRunConfiguration,
  env: ExecutionEnvironment,
  override val failedTests: Option[Seq[(String, String)]],
  runnerInfo: TestFrameworkRunnerInfo
) extends JavaCommandLineState(env)
  with ScalaTestFrameworkCommandLineStateLike{

  override def createJavaParameters(): JavaParameters = {
    val params = new JavaParametersModified()

    params.setCharset(null)

    val envs: Map[String, String] = VariablesExpander.getEnvVariablesExpanded(testConfigurationData)
    params.setEnv(envs.asJava)
    params.setPassParentEnvs(testConfigurationData.isPassParentEnvs)

    val vmParams: String = VariablesExpander.expandVmOptions(testConfigurationData.javaOptions, envs)
    params.getVMParametersList.addParametersString(vmParams)

    // automatically fiters non-applicable extensions
    JavaRunConfigurationExtensionManager.getInstance().updateJavaParameters(configuration, params, getRunnerSettings, env.getExecutor)

    if (DebugOptions.attachDebugAgent) {
      val suspend = if(DebugOptions.waitUntilDebuggerAttached) "y" else "n"
      params.getVMParametersList.addParametersString(s"-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspend,address=${DebugOptions.port}")
    }

    val workingDirEffective = VariablesExpander.getWorkingDirExpanded(configuration)
    params.setWorkingDirectory(workingDirEffective)

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

    if (testConfigurationData.searchTestsInWholeProject) {
      def anyJdk: Option[Sdk] = {
        val modules = project.modules.iterator.filterNot(_.isBuildModule)
        modules.map(JavaParameters.getValidJdkToRunModule(_, false)).nextOption()
      }
      val sdk = maybeCustomSdk.orElse(anyJdk)
      // TODO: handle case if  project contains multiple scala versions, runtime contains garbage with multiple versions
      params.configureByProject(project, JavaParameters.JDK_AND_CLASSES_AND_TESTS, sdk.orNull)
    } else {
      val sdk = maybeCustomSdk.getOrElse(JavaParameters.getValidJdkToRunModule(module, false))
      params.configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS, sdk)
    }

    val suitesToTestsMap = buildSuitesToTestsMap
    val programParameters = buildProgramParameters(suitesToTestsMap)
    val useTestsArgsFile = {
      // multiple test classes can blow command line length
      // it can be observed when running tests in "all in package", "all in project" mode
      val hasMultipleTestsClasses = suitesToTestsMap.size > 1
      hasMultipleTestsClasses
    }
    if (useTestsArgsFile) {
      val argsFile = prepareTempArgsFile(programParameters.testsArgs)
      params.getProgramParametersList.add(s"@${argsFile.getAbsolutePath}")
      params.getProgramParametersList.addAll(programParameters.otherArgs: _*)
    } else {
      params.getProgramParametersList.addAll(programParameters.allArgs: _*)
    }

    params.setShortenCommandLine(configuration.getShortenCommandLine, project)

    params
  }

  /**
   * Process command line has length limitation (on windows it's max 32768 characters).
   * When we run many tests (e.g. in "all in package", "all in project" mode) we can potentially pass a lot of
   * command line parameters to test runners which will lead to exception during process creation.
   *
   * We could use [[ShortenCommandLine]] to solve the issue, but it only focuses on shortening of a classpath.<br>
   * Even [[ShortenCommandLine.ARGS_FILE @argfile]] only shortens classpath (IDEA-249722). Besides, it works with Java 9+ only.
   *
   * To support running many tests with any JDK and with any shortening mode we use custom arg file feature.
   */
  private def prepareTempArgsFile(
    programParameters: Seq[String]
  ): File = try {
    val tempFile: File = File.createTempFile("idea_scala_test_runner", ".tmp")
    Using.resource(new PrintStream(tempFile, StandardCharsets.UTF_8.toString)) { printer =>
      programParameters.foreach(printer.println)
    }
    tempFile
  } catch {
    case e: IOException =>
      throw executionException("Failed to create temporary tests args file", e)
  }

  private def buildProgramParameters(suitesToTests: Map[String, Set[String]]): ScalaTestRunnerProgramArgs = {
    val classesAndTests = buildClassesAndTestsParameters(suitesToTests)
    val progress = Seq("-showProgressMessages", testConfigurationData.showProgressMessages.toString)
    val other = ParametersList.parse(testConfigurationData.testArgs)
    ScalaTestRunnerProgramArgs(
      classesAndTests,
      (progress ++ other).map(VariablesExpander.expandProgramArgument)
    )
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
    val processHandler = startProcess()

    attachExtensionsToProcess(configuration, processHandler)

    RawProcessOutputDebugLogger.maybeAddListenerTo(processHandler)

    val consoleView: BaseTestsOutputConsoleView = {
      val consoleProperties = configuration.createTestConsoleProperties(executor)
      consoleProperties.setIdBasedTestTree(true)
      SMTestRunnerConnectionUtil.createConsole("Scala", consoleProperties)
    }
    /**
     * This, for example, can attach additional profiler window when the test is executed
     * using "Profiler ... with IntelliJ Profiler". The window shows live process CPU & Memory usage.
     *
     * Copied from [[com.intellij.execution.JavaTestFrameworkRunnableState#execute]]
     */
    val consoleViewDecorated =
      if (ApplicationManager.getApplication.isUnitTestMode) {
        // Don't decorate teh console in tests - it can try to create some UI which will lead to runtime exceptions
        // E.g. in JavaProfilerConsoleWidgetManager
        consoleView
      } else
        JavaRunConfigurationExtensionManager.getInstance.decorateExecutionConsole(
          configuration,
          getRunnerSettings,
          consoleView,
          executor
        )
    Disposer.register(configuration.getProject, consoleViewDecorated)

    consoleViewDecorated.attachToProcess(processHandler)

    val executionResult = createExecutionResult(consoleViewDecorated, processHandler)

    executionResult
  }

  /**
   * @see [[org.jetbrains.plugins.scala.testingSupport.test.ui.CommonScalaParametersPanel.isMacroSupportEnabled]]
   * @see [[org.jetbrains.plugins.scala.testingSupport.test.ui.CommonScalaParametersPanel.initMacroSupport]]
   */
  protected object VariablesExpander {
    import com.intellij.execution.util.ProgramParametersUtil

    /** Expands all kinds of path variables/macro in working directory, examples: $PROJECT_DIR$, $MODULE_WORKING_DIR$, etc... */
    def getWorkingDirExpanded(configuration: AbstractTestRunConfiguration): String =
      ProgramParametersUtil.getWorkingDir(configuration.testConfigurationData, project, module)

    def expandProgramArgument(arg: String): String =
      ProgramParametersUtil.expandPathAndMacros(arg, module, project)

    /** expands path, macro and env variables */
    def expandVmOptions(vmOptions: String, envs: Map[String, String]): String = {
      val expandedPaths = ProgramParametersUtil.expandPathAndMacros(vmOptions, module, project)
      //injection of environment variables to VM options was added within SCL-4812
      // I am not sure why, because no one asked for that, and it doesn't work for any other Run Configuration ATM
      val expandedEnvs = expandEnvs(expandedPaths, envs)
      expandedEnvs
    }

    /**
     * Expands parents environment variables and paths <br>
     * Similar logic is located in [[com.intellij.execution.util.ProgramParametersConfigurator#configureConfiguration]]<br>
     * It might be Exported to some utility method in [[ProgramParametersUtil]]
     */
    def getEnvVariablesExpanded(configuration: CommonProgramRunConfigurationParameters): Map[String, String] = {
      val envs = new ju.HashMap[String, String](configuration.getEnvs)
      if (configuration.isPassParentEnvs) {
        EnvironmentUtil.inlineParentOccurrences(envs)
      }
      envs.asScala.view.mapValues(ProgramParametersUtil.expandPath(_, module, project)).toMap
    }
  }
}

object ScalaTestFrameworkCommandLineState {

  private def expandEnvs(text: String, envs: collection.Map[String, String]): String =
    envs.foldLeft(text) { case (text, (key, value)) =>
      StringUtil.replace(text, "$" + key + "$", value, false)
    }

  // ScalaTest does not understand backticks in class/package qualified names, it will fail to run
  private def sanitize(qualifiedName: String): String = qualifiedName.replace("`", "")

  private case class ScalaTestRunnerProgramArgs(
    testsArgs: Seq[String],
    otherArgs: Seq[String]
  ) {
    def allArgs: Seq[String] = testsArgs ++ otherArgs
  }
}
