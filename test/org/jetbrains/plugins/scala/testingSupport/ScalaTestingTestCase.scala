package org.jetbrains.plugins.scala
package testingSupport

import java.util.concurrent.atomic.AtomicReference

import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.DefaultJavaProgramRunner
import com.intellij.execution.process.{ProcessEvent, ProcessAdapter, ProcessHandler, ProcessListener}
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.openapi.util.Key
import com.intellij.execution.runners.{ExecutionEnvironmentBuilder, ProgramRunner}
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.{Location, PsiLocation, Executor, RunnerAndConfigurationSettings}
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.{PsiElement, PsiManager}
import com.intellij.testFramework.{PsiTestUtil, UsefulTestCase}
import com.intellij.util.concurrency.Semaphore
import org.jetbrains.plugins.scala.debugger.ScalaDebuggerTestBase
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, AbstractTestConfigurationProducer}
import org.jetbrains.plugins.scala.util.TestUtils

/**
 * @author Roman.Shein
 *         Date: 03.03.14
 */
abstract class ScalaTestingTestCase(private val configurationProducer: AbstractTestConfigurationProducer) extends ScalaDebuggerTestBase with IntegrationTest {

  protected def addIvyCacheLibrary(libraryName: String, libraryPath: String, jarNames: String*) {
    val libsPath = TestUtils.getIvyCachePath
    val pathExtended = s"$libsPath/$libraryPath/"
    VfsRootAccess.allowRootAccess(pathExtended)
    PsiTestUtil.addLibrary(myModule, libraryName, pathExtended, jarNames: _*)
  }

  override val testDataBasePrefix = "testingSupport"

  protected val useDynamicClassPath = false

  override protected def createLocation(lineNumber: Int, offset: Int, fileName: String): PsiLocation[PsiElement] = {
    val ioFile = new java.io.File(srcDir, fileName)

    val file = getVirtualFile(ioFile)

    val project = getProject

    val myManager = PsiManager.getInstance(project)

    val psiFile = myManager.findViewProvider(file).getPsi(ScalaFileType.SCALA_LANGUAGE)

    new PsiLocation(project, myModule, psiFile.findElementAt(FileDocumentManager.getInstance().
        getDocument(file).getLineStartOffset(lineNumber) + offset))
  }

  override protected def createTestFromLocation(lineNumber: Int, offset: Int, fileName: String): RunnerAndConfigurationSettings =
    configurationProducer.createConfigurationByLocation(createLocation(lineNumber, offset, fileName))

  override protected def runTestFromConfig(
                                   configurationCheck: RunnerAndConfigurationSettings => Boolean,
                                   runConfig: RunnerAndConfigurationSettings,
                                   checkOutputs: Boolean = false,
                                   duration: Int = 3000,
                                   debug: Boolean = false
                                   ): (String, Option[AbstractTestProxy]) = {
    assert(configurationCheck(runConfig))
    val testResultListener = new TestResultListener(runConfig.getName)
    var testTreeRoot: Option[AbstractTestProxy] = None
    UsefulTestCase.edt(new Runnable {
      def run() {
        if (needMake) {
          make()
          saveChecksums()
        }
        val runner = ProgramRunner.PROGRAM_RUNNER_EP.getExtensions.find {
          _.getClass == classOf[DefaultJavaProgramRunner]
        }.get
        val (handler, runContentDescriptor) = runProcess(runConfig, classOf[DefaultRunExecutor], new ProcessAdapter {
          override def onTextAvailable(event: ProcessEvent, outputType: Key[_]) {
            val text = event.getText
            if (debug) print(text)
          }
        }, runner)

        runContentDescriptor.getExecutionConsole match {
          case descriptor: SMTRunnerConsoleView =>
            testTreeRoot = Some(descriptor.getResultsViewer.getRoot)
          case _ =>
        }
        handler.addProcessListener(testResultListener)
      }
    })

    val res = testResultListener.waitForTestEnd(duration)

    (res, testTreeRoot)
  }

  private def runProcess(runConfiguration: RunnerAndConfigurationSettings,
                           executorClass: Class[_ <: Executor],
                           listener: ProcessListener,
                           runner: ProgramRunner[_ <: RunnerSettings]): (ProcessHandler, RunContentDescriptor) = {
    val configuration = runConfiguration.getConfiguration
    val executor: Executor = Executor.EXECUTOR_EXTENSION_NAME.findExtension(executorClass)
    val executionEnvironmentBuilder: ExecutionEnvironmentBuilder =
      new ExecutionEnvironmentBuilder(configuration.getProject, executor)
    executionEnvironmentBuilder.runProfile(configuration)
    val semaphore: Semaphore = new Semaphore
    semaphore.down()
    val processHandler: AtomicReference[ProcessHandler] = new AtomicReference[ProcessHandler]
    val contentDescriptor: AtomicReference[RunContentDescriptor] = new AtomicReference[RunContentDescriptor]
    runner.execute(executionEnvironmentBuilder.build, new ProgramRunner.Callback {
      def processStarted(descriptor: RunContentDescriptor) {
        System.setProperty("idea.dynamic.classpath", useDynamicClassPath.toString)
        disposeOnTearDown(new Disposable {
          def dispose() {
            descriptor.dispose()
          }
        })
        val handler: ProcessHandler = descriptor.getProcessHandler
        assert(handler != null)
        handler.addProcessListener(listener)
        processHandler.set(handler)
        contentDescriptor.set(descriptor)
        semaphore.up()
      }
    })
    semaphore.waitFor()
    (processHandler.get, contentDescriptor.get)
  }
}
