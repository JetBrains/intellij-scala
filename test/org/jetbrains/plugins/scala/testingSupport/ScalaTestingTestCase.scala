package org.jetbrains.plugins.scala
package testingSupport

import java.util.concurrent.atomic.AtomicReference

import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.DefaultJavaProgramRunner
import com.intellij.execution.process.{ProcessAdapter, ProcessEvent, ProcessHandler, ProcessListener}
import com.intellij.execution.runners.{ExecutionEnvironmentBuilder, ProgramRunner}
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.{Executor, PsiLocation, RunnerAndConfigurationSettings}
import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.ide.util.treeView.smartTree.{NodeProvider, TreeElement, TreeElementWrapper}
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Key
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.psi.{PsiDirectory, PsiElement, PsiManager}
import com.intellij.testFramework.EdtTestUtil
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.Semaphore
import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestBase, ScalaVersion_2_11}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.structureView.ScalaStructureViewModel
import org.jetbrains.plugins.scala.lang.structureView.elements.impl.TestStructureViewElement
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestConfigurationProducer, AbstractTestRunConfiguration}

/**
  * @author Roman.Shein
  *         Date: 03.03.14
  */
abstract class ScalaTestingTestCase extends ScalaDebuggerTestBase with IntegrationTest with ScalaVersion_2_11 {

  protected val configurationProducer: AbstractTestConfigurationProducer

  override def runInDispatchThread(): Boolean = false

  override protected def addFileToProject(fileName: String, fileText: String) = {
    EdtTestUtil.runInEdtAndWait(new ThrowableRunnable[Throwable] {
      override def run(): Unit = {
        ScalaTestingTestCase.super.addFileToProject(fileName, fileText)
      }
    })
  }

  override val testDataBasePrefix = "testingSupport"

  protected val useDynamicClassPath = false

  override protected def runFileStructureViewTest(testClassName: String, status: Int, tests: String*) = {
    val structureViewRoot = buildFileStructure(testClassName + ".scala")
    for (test <- tests) {
      assert(checkTestNodeInFileStructure(structureViewRoot, test, None, status))
    }
  }

  override def invokeTestRunnable(runnable: Runnable): Unit = runnable.run()

  override protected def runFileStructureViewTest(testClassName: String, testName: String, parentTestName: Option[String],
                                                  testStatus: Int = TestStructureViewElement.normalStatusId) = {
    val structureViewRoot = buildFileStructure(testClassName + ".scala")
    assert(checkTestNodeInFileStructure(structureViewRoot, testName, parentTestName, testStatus))
  }

  override protected def buildFileStructure(fileName: String): TreeElementWrapper = {
    val ioFile = new java.io.File(srcDir, fileName)
    var wrapper: StructureViewComponent.StructureViewTreeElementWrapper = null
    EdtTestUtil.runInEdtAndWait(new ThrowableRunnable[Throwable] {
      override def run(): Unit = {
        val file = PsiManager.getInstance(getProject).findFile(getVirtualFile(ioFile))
        val treeViewModel = new ScalaStructureViewModel(file.asInstanceOf[ScalaFile]) {
          override def isEnabled(provider: NodeProvider[_ <: TreeElement]): Boolean = provider.isInstanceOf[TestNodeProvider]
        }
        wrapper = new StructureViewComponent.StructureViewTreeElementWrapper(getProject, treeViewModel.getRoot, treeViewModel)

        def initTree(wrapper: StructureViewComponent.StructureViewTreeElementWrapper) {
          import scala.collection.JavaConversions._
          wrapper.initChildren()
          wrapper.getChildren.toList.foreach(node => initTree(node.asInstanceOf[StructureViewComponent.StructureViewTreeElementWrapper]))
        }
        initTree(wrapper)
      }
    })
    wrapper
  }

  override protected def createLocation(lineNumber: Int, offset: Int, fileName: String): PsiLocation[PsiElement] = {
    val ioFile = new java.io.File(srcDir, fileName)

    val file = getVirtualFile(ioFile)

    val project = getProject

    val myManager = PsiManager.getInstance(project)

    var psiElement: PsiElement = null

    EdtTestUtil.runInEdtAndWait(new ThrowableRunnable[Throwable] {
      override def run(): Unit = {
        val psiFile = myManager.findViewProvider(file).getPsi(ScalaLanguage.INSTANCE)
        psiElement = psiFile.findElementAt(FileDocumentManager.getInstance().getDocument(file).
          getLineStartOffset(lineNumber) + offset)
      }
    })

    new PsiLocation(project, myModule, psiElement)
  }

  private def failedConfigMessage(fileName: String, lineNumber: Int, offset: Int) =
    "Failed to create run configuration for test from file " + fileName + " from line " + lineNumber + " at offset " + offset

  private def failedConfigMessage(packageName: String) = "Failed to create run configuration for test from package " + packageName

  override protected def createTestFromLocation(lineNumber: Int, offset: Int, fileName: String): RunnerAndConfigurationSettings = {
    var res: RunnerAndConfigurationSettings = null
    EdtTestUtil.runInEdtAndWait(new ThrowableRunnable[Throwable] {
      override def run(): Unit = {
        res = configurationProducer.createConfigurationByLocation(createLocation(lineNumber, offset, fileName)).map(_._2) match {
          case Some(testConfig) => testConfig
          case _ => throw new RuntimeException(failedConfigMessage(fileName, lineNumber, offset))
        }
      }
    })
    res
  }

  override protected def createTestFromPackage(packageName: String): RunnerAndConfigurationSettings =
    createTestFromDirectory(
      ScalaPsiManager.instance(getProject).getCachedPackage(packageName) match {
        case Some(myPackage) => myPackage.getDirectories().head
        case _ => throw new RuntimeException(failedConfigMessage(packageName))
      }
    )

  override protected def createTestFromModule(moduleName: String): RunnerAndConfigurationSettings = {
    var module: Module = null
    EdtTestUtil.runInEdtAndWait(new ThrowableRunnable[Throwable] {
      override def run(): Unit =
        module = ModuleManager.getInstance(ScalaTestingTestCase.this.getProject).findModuleByName(moduleName)
    })
    createTestFromDirectory(PsiDirectoryFactory.getInstance(getProject).
      createDirectory(ModuleRootManager.getInstance(module).getContentRoots.head))
  }

  private def createTestFromDirectory(directory: PsiDirectory) =
    configurationProducer.createConfigurationByLocation(new PsiLocation(getProject, directory)).map(_._2) match {
      case Some(testConfig) => testConfig
      case _ => throw new RuntimeException(failedConfigMessage(directory.getName))
    }

  override protected def runTestFromConfig(configurationCheck: RunnerAndConfigurationSettings => Boolean,
                                           runConfig: RunnerAndConfigurationSettings,
                                           checkOutputs: Boolean = false,
                                           duration: Int = 3000,
                                           debug: Boolean = false
                                          ): (String, Option[AbstractTestProxy]) = {
    assert(configurationCheck(runConfig))
    assert(runConfig.getConfiguration.isInstanceOf[AbstractTestRunConfiguration])
    runConfig.getConfiguration.asInstanceOf[AbstractTestRunConfiguration].setupIntegrationTestClassPath()
    val testResultListener = new TestResultListener(runConfig.getName)
    var testTreeRoot: Option[AbstractTestProxy] = None
    EdtTestUtil.runInEdtAndWait(new ThrowableRunnable[Throwable] {
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

    (testResultListener.waitForTestEnd(duration), testTreeRoot)
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
