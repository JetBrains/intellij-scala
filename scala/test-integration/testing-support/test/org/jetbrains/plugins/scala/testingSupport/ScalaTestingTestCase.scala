package org.jetbrains.plugins.scala.testingSupport

import com.intellij.execution.actions.{ConfigurationContext, ConfigurationFromContext}
import com.intellij.execution.configurations.{JavaCommandLineState, RunConfiguration, RunProfileState, RunnerSettings}
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.DefaultJavaProgramRunner
import com.intellij.execution.process.{ProcessHandler, ProcessListener}
import com.intellij.execution.runners.{ExecutionEnvironmentBuilder, ProgramRunner}
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.ui.{ExecutionConsole, RunContentDescriptor}
import com.intellij.execution.{Executor, PsiLocation, RunnerAndConfigurationSettings}
import com.intellij.openapi.Disposable
import com.intellij.psi.PsiElement
import com.intellij.testFramework.EdtTestUtil
import com.intellij.util.concurrency.Semaphore
import org.jetbrains.plugins.scala.TestingSupportTests
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.compiler.{ScalaExecutionTestCase, ScalaExecutionTestUtils}
import org.jetbrains.plugins.scala.configurations.RunConfigCreationContext
import org.jetbrains.plugins.scala.configurations.RunConfigCreationLocation.CaretLocation
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.util.assertions.failWithCause
import org.junit.Assert
import org.junit.Assert._
import org.junit.experimental.categories.Category

import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.{Failure, Try}

@Category(Array(classOf[TestingSupportTests]))
abstract class ScalaTestingTestCase
  extends ScalaExecutionTestCase
    with IntegrationTest
    with FileStructureTest
    with ScalaSdkOwner
    with TestOutputMarkers {

  protected def expectedDefaultRunConfigurationClass: Class[_ <: RunConfiguration]

  override def runInDispatchThread(): Boolean = false

  override def areLogErrorsIgnored(): Boolean = true

  /** if set to true, prints raw output of test process to console */
  final def debugProcessOutput = false

  override protected def testDataDirectoryName: String = "testingSupport"

  protected val useDynamicClassPath = false

  protected def createPsiLocation(location: CaretLocation): PsiLocation[PsiElement] =
    createPsiLocation(location, myModule, srcPath)

  protected def selectSingleConfigurationOfExpectedTypeOrFail(
    context: ConfigurationContext,
    configCreationContext: RunConfigCreationContext
  ): ConfigurationFromContext = {
    val configurations: Seq[ConfigurationFromContext] =
      Option(context.getConfigurationsFromContext).toSeq.flatMap(_.asScala)

    if (configurations.isEmpty) {
      throw new AssertionError(s"No configuration created from context: $context")
    }

    // If preferredConfigClass is set     : allow multiple run configurations created, but only a single configuration of the preferred class
    // If preferredConfigClass is NOT set : don't allow multiple run configurations, even with different classes
    val configurationsWithPreferredClassOrAll = configCreationContext.preferredConfigClass match {
      case Some(preferredConfigClass) =>
        configurations.filter(c => preferredConfigClass.isInstance(c.getConfiguration))
      case _ =>
        configurations
    }

    selectSingleConfigurationOfExpectedTypeOrFail(configurationsWithPreferredClassOrAll, configCreationContext.preferredConfigClass)
  }

  private def selectSingleConfigurationOfExpectedTypeOrFail(
    configurations: Seq[ConfigurationFromContext],
    preferredClass: Option[Class[_ <: RunConfiguration]]
  ): ConfigurationFromContext = {
    configurations match {
      case Seq(config) =>
        assertConfigurationType(config, preferredClass.getOrElse(expectedDefaultRunConfigurationClass))
        config
      case multipleConfigs =>
        // We show a hint about the preferred class only if it's set.
        // Otherwise, don't remind about the expectedDefaultRunConfigurationClass as we don't expect multiple configurations even of different classes
        val ofClassHint = preferredClass.map(clazz => s" of class $clazz").getOrElse("")
        throw new AssertionError(
          s"""Multiple run configurations of class$ofClassHint created when only a single one expected
             |${multipleConfigs.map(_.toString).mkString("\n")}""".stripMargin
        )
    }
  }

  private def assertConfigurationType(config: ConfigurationFromContext, expectedConfigClass: Class[_ <: RunConfiguration]): Unit = {
    Assert.assertEquals(
      s"Created run configuration has an unexpected class",
      expectedConfigClass,
      config.getConfiguration.getClass,
    )
  }

  protected def assertNoConfigurationCreatedAtCaret(location: CaretLocation): Unit =
    inReadAction {
      val psiElement = findPsiElement(location, getProject, srcPath)
      if (psiElement == null)
        return // good: no element -> no configuration created

      val context: ConfigurationContext = new ConfigurationContext(psiElement)
      val configurationsFromContext = Option(context.getConfigurationsFromContext).toSeq.flatMap(_.asScala)
      val relevantConfigs = configurationsFromContext.filter(c => expectedDefaultRunConfigurationClass.isInstance(c.getConfiguration))

      if (relevantConfigs.nonEmpty) {
        fail(
          s"""Expected no run configuration to be created at location $location, but found ${relevantConfigs.size} configuration(s):
             |${relevantConfigs.map(_.toString).mkString("\n")}""".stripMargin
        )
      }
    }

  protected def assertNoConfigurationCreatedInTheBeginningOfEachLineInFile(fileName: String): Unit = {
    val lineCount = numberOfLinesInTestFile(fileName)
    for (line <- 0 until lineCount) {
      assertNoConfigurationCreatedAtCaret(loc(fileName, line, 0))
    }
  }

  private def numberOfLinesInTestFile(fileName: String): Int = {
    import com.intellij.openapi.editor.Document
    import com.intellij.openapi.fileEditor.FileDocumentManager

    val vFile = findTestFile(srcPath, fileName)
    val document: Document = inReadAction {
      FileDocumentManager.getInstance().getDocument(vFile)
    }
    document.getLineCount
  }

  protected final def runTestFromConfig(
    runConfig: RunnerAndConfigurationSettings
  )(implicit testOptions: TestRunOptions): TestRunResult =
    runTestFromConfig(runConfig, testOptions.duration)

  // JavaConsoleWithProfilerWidget is an Ultimate-only class, we can only check it at runtime.
  // Otherwise, the community repo cannot be built on its own.
  private def isJavaConsoleWithProfilerWidget(console: ExecutionConsole): Boolean =
    console.getClass.getName == "com.intellij.profiler.ultimate.widget.JavaConsoleWithProfilerWidget"

  override protected def runTestFromConfig(
    runConfig: RunnerAndConfigurationSettings,
    duration: FiniteDuration,
  ): TestRunResult = {
    val testResultListener = new TestRunnerOutputListener(debugProcessOutput)
    val testStatusListener = new TestStatusListener
    val exitCodeListener = new ProcessFinishedListener
    var testTreeRoot: Option[AbstractTestProxy] = None

    runConfig.getConfiguration.getProject
      .getMessageBus
      .connect(getTestRootDisposable)
      .subscribe(SMTRunnerEventsListener.TEST_STATUS, testStatusListener)

    val (handler, _) = EdtTestUtil.runInEdtAndGet(() => {
      val runner = ProgramRunner.PROGRAM_RUNNER_EP.getExtensions.find(_.getClass == classOf[DefaultJavaProgramRunner]).get
      val (handler, runContentDescriptor) = runProcess(runConfig, classOf[DefaultRunExecutor], runner, Seq(testResultListener, exitCodeListener))

      runContentDescriptor.getExecutionConsole match {
        case widget if isJavaConsoleWithProfilerWidget(widget) =>
          Try(widget.getClass.getDeclaredField("console")).foreach { consoleField =>
            Try {
              consoleField.setAccessible(true)
              consoleField.get(widget) match {
                case console: SMTRunnerConsoleView =>
                  testTreeRoot = Some(console.getResultsViewer.getRoot)
              }
            }
          }
        case descriptor: SMTRunnerConsoleView =>
          testTreeRoot = Some(descriptor.getResultsViewer.getRoot)
        case _ =>
      }

      (handler, runContentDescriptor)
    })

    val exitCode = waitForTestEnd(handler, exitCodeListener, duration)

    val result = TestRunResult(
      runConfig,
      exitCode.getOrElse(-1),
      ProcessOutput(
        testResultListener.outputText,
        testResultListener.outputTextFromTests,
        testStatusListener.uncapturedOutput,
      ),
      testTreeRoot,
    )

    exitCode match {
      case Failure(exception) =>
        result.printOutputDetailsToConsole()
        val message = s"test `${runConfig.getName}` did not terminate correctly after ${duration.toMillis} ms"
        failWithCause(message, exception)
      case _ =>
    }

    result
  }

  private def waitForTestEnd(
    handler: ProcessHandler,
    exitCodeListener: ProcessFinishedListener,
    duration: FiniteDuration
  ): Try[Int] = {
    val exitCode = Try(Await.result(exitCodeListener.exitCodeFuture, duration))
    // in case of unprocessed output we want to wait for the process end until the project is disposed
    val processInput = handler.getProcessInput
    if (processInput != null) processInput.flush()

    if (!handler.isProcessTerminated) {
      ScalaExecutionTestUtils.printThreadDumpAfterTimeout(handler)
    }

    handler.destroyProcess()
    exitCode
  }

  private def runProcess(
    runConfiguration: RunnerAndConfigurationSettings,
    executorClass: Class[_ <: Executor],
    runner: ProgramRunner[_ <: RunnerSettings],
    listeners: Seq[ProcessListener],
  ): (ProcessHandler, RunContentDescriptor) = {
    val executionEnvironment = {
      val configuration = runConfiguration.getConfiguration
      val executor: Executor = Executor.EXECUTOR_EXTENSION_NAME.findExtension(executorClass)
      val builder = new ExecutionEnvironmentBuilder(configuration.getProject, executor)
      builder.runProfile(configuration)
      builder.build()
    }

    val processHandler: AtomicReference[ProcessHandler] = new AtomicReference[ProcessHandler]
    val contentDescriptor: AtomicReference[RunContentDescriptor] = new AtomicReference[RunContentDescriptor]

    val semaphore = new Semaphore(1)

    //noinspection ApiStatus
    executionEnvironment.setCallback { (descriptor: RunContentDescriptor) =>
      System.setProperty("idea.dynamic.classpath", useDynamicClassPath.toString)
      val handler: ProcessHandler = descriptor.getProcessHandler
      assertNotNull(handler)
      disposeOnTearDown(new Disposable {
        override def dispose(): Unit = {
          if (!handler.isProcessTerminated)
            handler.destroyProcess()
          descriptor.dispose()
        }
      })
      listeners.foreach(handler.addProcessListener)

      processHandler.set(handler)
      contentDescriptor.set(descriptor)

      semaphore.up()
    }

    val state = executionEnvironment.getState
    if (state != null) {
      ensureWorkingDirectoryExists(state)
    }

    runner.execute(executionEnvironment)

    semaphore.waitFor()

    (processHandler.get, contentDescriptor.get)
  }

  private def ensureWorkingDirectoryExists(state: RunProfileState): Any = {
    state match {
      // Examples of such state:
      //  - org.jetbrains.plugins.scala.testingSupport.test.ScalaTestFrameworkCommandLineState
      //  - com.intellij.execution.junit.TestPackage
      case state: JavaCommandLineState =>
        Try {
          val workingDir = state.getJavaParameters.getWorkingDirectory
          val path = Path.of(workingDir)
          if (!Files.exists(path)) {
            Files.createDirectories(path)
          }
        }

      case _ =>
    }
  }
}