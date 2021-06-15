package org.jetbrains.plugins.scala.testingSupport.test

import java.io.{File, FileOutputStream, IOException, PrintStream}
import java.{util => ju}

import com.intellij.execution.configurations.{JavaCommandLineState, JavaParameters, ParametersList}
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.{ExecutionResult, Executor, JavaRunConfigurationExtensionManager, RunConfigurationExtension, ShortenCommandLine}
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.extensions.{IteratorExt, ObjectExt}
import org.jetbrains.plugins.scala.project.{ModuleExt, PathsListExt, ProjectExt}
import org.jetbrains.plugins.scala.testingSupport.test.CustomTestRunnerBasedStateProvider.TestFrameworkRunnerInfo
import org.jetbrains.plugins.scala.testingSupport.test.ScalaTestFrameworkCommandLineState._
import org.jetbrains.plugins.scala.testingSupport.test.exceptions.executionException
import org.jetbrains.plugins.scala.testingSupport.test.utils.{JavaParametersModified, RawProcessOutputDebugLogger}

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

    val envs = new ju.HashMap[String, String](testConfigurationData.envs)
    params.setEnv(envs)

    val vmParams = {
      val params0 = testConfigurationData.javaOptions
      val params1 = expandPath(params0)
      expandEnvs(params1, envs.asScala)
    }
    params.getVMParametersList.addParametersString(vmParams)

    // automatically fiters non-applicable extensions
    JavaRunConfigurationExtensionManager.getInstance().updateJavaParameters(configuration, params, getRunnerSettings, env.getExecutor)

    if (DebugOptions.attachDebugAgent) {
      val suspend = if(DebugOptions.waitUntilDebuggerAttached) "y" else "n"
      params.getVMParametersList.addParametersString(s"-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspend,address=5009")
    }

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

    if (testConfigurationData.searchTestsInWholeProject) {
      def anyJdk: Option[Sdk] = {
        val modules = project.modules.iterator.filterNot(_.isBuildModule)
        modules.map(JavaParameters.getValidJdkToRunModule(_, false)).headOption
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
    Using.resource(new PrintStream(new FileOutputStream(tempFile))) { printer =>
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
      progress ++ other
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

    val consoleView = {
      val consoleProperties = configuration.createTestConsoleProperties(executor)
      consoleProperties.setIdBasedTestTree(true)
      SMTestRunnerConnectionUtil.createConsole("Scala", consoleProperties)
    }
    consoleView.attachToProcess(processHandler)

    val executionResult = createExecutionResult(consoleView, processHandler)

    executionResult
  }
}

object ScalaTestFrameworkCommandLineState {

  private def expandPath(text: String)(implicit module: Module): String =
    Some(text)
      .map(PathMacroManager.getInstance(module.getProject).expandPath)
      .map(PathMacroManager.getInstance(module).expandPath)
      .get

  private def expandEnvs(text: String, envs: collection.Map[String, String]) =
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
