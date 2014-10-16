package org.jetbrains.plugins.scala
package testingSupport

import org.jetbrains.plugins.scala.debugger.ScalaDebuggerTestBase
import com.intellij.testFramework.{PsiTestUtil, UsefulTestCase}
import com.intellij.execution.runners.{ExecutionEnvironmentBuilder, ProgramRunner}
import com.intellij.debugger.impl.GenericDebuggerRunner
import com.intellij.execution.executors.{DefaultRunExecutor, DefaultDebugExecutor}
import com.intellij.execution.process.{ProcessHandler, ProcessListener, ProcessEvent, ProcessAdapter}
import com.intellij.openapi.util.Key
import com.intellij.execution.impl.DefaultJavaProgramRunner
import com.intellij.execution.{RunnerAndConfigurationSettings, Executor, PsiLocation}
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.{PsiManager, SingleRootFileViewProvider, PsiFileFactory, PsiElement}
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.util.LocalTimeCounter
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, AbstractTestConfigurationProducer}
import com.intellij.openapi.module.Module
import com.intellij.execution.application.{ApplicationConfigurationType, ApplicationConfiguration}
import com.intellij.execution.configurations.{RunConfiguration, RunnerSettings, RunProfile}
import com.intellij.util.concurrency.Semaphore
import java.util.concurrent.atomic.AtomicReference
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.Disposable
import com.intellij.execution.testframework.sm.runner.ui.{SMTestRunnerResultsForm, SMTRunnerConsoleView}
import com.intellij.execution.testframework.{AbstractTestProxy, TestTreeView}
import com.intellij.execution.junit2.ui.JUnitTreeConsoleView
import scala.annotation.tailrec
import scala.annotation.tailrec
import javax.swing.SwingUtilities
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.util.TestUtils
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import java.nio.file.Paths
import org.jetbrains.plugins.scala.config.FileAPI

/**
 * @author Roman.Shein
 *         Date: 03.03.14
 */
abstract class ScalaTestingTestCase(private val configurationProducer: AbstractTestConfigurationProducer) extends ScalaDebuggerTestBase {

  override val testDataBasePrefix = "testingSupport"

  protected val useDynamicClassPath = false

  protected def checkConfigAndSettings(configAndSettings: RunnerAndConfigurationSettings, testClass: String, testName: String): Boolean

  protected def checkConfig(testClass: String, testName: String, config: AbstractTestRunConfiguration): Boolean = {
    config.getTestClassPath == testClass && config.getTestName == testName
  }

  protected def checkResultTreeHasExactNamedPath(root: AbstractTestProxy, names: String*): Boolean =
    checkResultTreeHasExactNamedPath(root, names)

  protected def checkResultTreeDoesNotHaveNodes(root: AbstractTestProxy, names: String*): Boolean =
    checkResultTreeDoesNotHaveNodes(root, names)

  protected def checkResultTreeDoesNotHaveNodes(root: AbstractTestProxy, names: Iterable[String]): Boolean = {
    import scala.collection.JavaConversions._
    if (root.isLeaf && !names.contains(root.getName)) true
    else !names.contains(root.getName) && root.getChildren.toList.forall(checkResultTreeDoesNotHaveNodes(_, names))
  }

  protected def checkResultTreeHasExactNamedPath(root: AbstractTestProxy, names: Iterable[String]): Boolean = {
    @tailrec
    def buildConditions(names: Iterable[String], acc: List[AbstractTestProxy => Boolean] = List()):
    List[AbstractTestProxy => Boolean] = names.size match {
      case 0 => List(_ => true) //got an empty list of names as initial input
      case 1 =>
        ((node: AbstractTestProxy) => node.getName == names.head && node.isLeaf) :: acc //last element must be leaf
      case _ => buildConditions(names.tail,
        ((node: AbstractTestProxy) => node.getName == names.head && !node.isLeaf) :: acc)
    }
    checkResultTreeHasPath(root, buildConditions(names).reverse)
  }

  protected def checkResultTreeHasPath(root: AbstractTestProxy, conditions: Iterable[AbstractTestProxy => Boolean]): Boolean = {
    import scala.collection.JavaConversions._
    val curRes = conditions.head(root)
    curRes && (root.getChildren.isEmpty && conditions.size == 1 ||
        root.getChildren.toList.exists(checkResultTreeHasPath(_, conditions.tail)))
  }

  protected def runProcess(runConfiguration: RunnerAndConfigurationSettings,
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

  def runTestByLocation(lineNumber: Int, offset: Int, fileName: String,
                        configurationCheck: RunnerAndConfigurationSettings => Boolean,
                        testTreeCheck: AbstractTestProxy => Boolean,
                        expectedText: String = "OK", debug: Boolean = false, duration: Int = 3000,
                        checkOutputs: Boolean = false) = {

    val ioFile = srcDir.toPath.resolve(fileName).toFile

    val file = getVirtualFile(ioFile)

    val project = getProject

    val myManager = PsiManager.getInstance(project)

    val psiFile = myManager.findViewProvider(file).getPsi(ScalaFileType.SCALA_LANGUAGE)

    val location = new PsiLocation(project, myModule, psiFile.findElementAt(FileDocumentManager.getInstance().
        getDocument(file).getLineStartOffset(lineNumber) + offset))

    val runConfig = configurationProducer.createConfigurationByLocation(location)

    assert(configurationCheck(runConfig))

    val testResultListener = new TestResultListener(runConfig.getName)

    var testTreeRoot: Option[AbstractTestProxy] = None //TODO: move processing result tree to EDT thread

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

    SwingUtilities.invokeLater(new Runnable() {
      override def run(): Unit = {
        assert(testTreeRoot.isDefined && testTreeCheck(testTreeRoot.get))

        if (checkOutputs) {
          assert(res == expectedText)
        }
      }
    })
  }
}
