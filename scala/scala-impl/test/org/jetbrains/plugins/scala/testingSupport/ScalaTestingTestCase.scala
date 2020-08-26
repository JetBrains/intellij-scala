package org.jetbrains.plugins.scala
package testingSupport

import java.util.concurrent.atomic.AtomicReference

import com.intellij.execution.configurations.{ConfigurationType, RunnerSettings}
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.DefaultJavaProgramRunner
import com.intellij.execution.process.{ProcessAdapter, ProcessEvent, ProcessHandler, ProcessListener}
import com.intellij.execution.runners.{ExecutionEnvironmentBuilder, ProgramRunner}
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.{Executor, PsiLocation, RunnerAndConfigurationSettings}
import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.ide.util.treeView.smartTree.{NodeProvider, TreeElement, TreeElementWrapper}
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.psi.{PsiDirectory, PsiElement, PsiManager}
import com.intellij.testFramework.EdtTestUtil
import com.intellij.util.concurrency.Semaphore
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.debugger._
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.structureView.ScalaStructureViewModel
import org.jetbrains.plugins.scala.lang.structureView.element.Test
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestRunConfiguration
import org.jetbrains.plugins.scala.testingSupport.test.specs2.Specs2RunConfiguration
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider
import org.jetbrains.plugins.scala.testingSupport.test.utest.UTestRunConfiguration
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestConfigurationProducer, AbstractTestRunConfiguration}
import org.jetbrains.plugins.scala.util.assertions.failWithCause
import org.junit.Assert._
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters._
import scala.concurrent.Await
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.{Failure, Success, Try}

/**
  * @author Roman.Shein
  *         Date: 03.03.14
  */
@Category(Array(classOf[TestingSupportTests]))
abstract class ScalaTestingTestCase
  extends ScalaDebuggerTestBase
    with IntegrationTest
    with FileStructureTest
    with ScalaSdkOwner {

  protected val configurationProducer: AbstractTestConfigurationProducer[_]

  override def runInDispatchThread(): Boolean = false

  final def debugProcessOutput = false

  override protected def addFileToProjectSources(fileName: String, fileText: String): VirtualFile =
    EdtTestUtil.runInEdtAndGet { () =>
      ScalaTestingTestCase.super.addFileToProjectSources(fileName, fileText)
    }

  override val testDataBasePrefix = "testingSupport"

  protected val useDynamicClassPath = false
  
  override protected def runFileStructureViewTest(testClassName: String, status: Int, tests: String*): Unit = {
    val structureViewRoot = buildFileStructure(testClassName + ".scala")
    tests.foreach(assertTestNodeInFileStructure(structureViewRoot, _, None, status))
  }

  override protected def runFileStructureViewTest(testClassName: String, testName: String, parentTestName: Option[String],
                                                  testStatus: Int = Test.NormalStatusId): Unit = {
    val structureViewRoot = buildFileStructure(testClassName + ".scala")
    assertTestNodeInFileStructure(structureViewRoot, testName, parentTestName, testStatus)
  }

  override protected def buildFileStructure(fileName: String): TreeElementWrapper = {
    val ioFile = new java.io.File(srcDir, fileName)
    var wrapper: TreeElementWrapper = null
    EdtTestUtil.runInEdtAndWait(() => {
      val file = PsiManager.getInstance(getProject).findFile(getVirtualFile(ioFile))
      val treeViewModel = new ScalaStructureViewModel(file.asInstanceOf[ScalaFile]) {
        override def isEnabled(provider: NodeProvider[_ <: TreeElement]): Boolean = provider.isInstanceOf[TestNodeProvider]
      }
      wrapper = StructureViewComponent.createWrapper(getProject, treeViewModel.getRoot, treeViewModel)

      def initTree(wrapper: TreeElementWrapper): Unit = {
        wrapper.initChildren()
        wrapper.getChildren.asScala.foreach(node => initTree(node.asInstanceOf[TreeElementWrapper]))
      }

      initTree(wrapper)
    })
    wrapper
  }

  override protected def createLocation(lineNumber: Int, offset: Int, fileName: String): PsiLocation[PsiElement] = {
    val ioFile = new java.io.File(srcDir, fileName)

    val file = getVirtualFile(ioFile)

    val project = getProject

    val myManager = PsiManager.getInstance(project)

    val psiElement: PsiElement = EdtTestUtil.runInEdtAndGet { () =>
      val psiFile = myManager.findViewProvider(file).getPsi(ScalaLanguage.INSTANCE)
      val document = FileDocumentManager.getInstance().getDocument(file)
      val lineStartOffset = document.getLineStartOffset(lineNumber)
      psiFile.findElementAt(lineStartOffset + offset)
    }
    new PsiLocation(project, myModule, psiElement)
  }

  private def failedConfigMessage(fileName: String, lineNumber: Int, offset: Int) =
    "Failed to create run configuration for test from file " + fileName + " from line " + lineNumber + " at offset " + offset

  private def failedConfigMessage(packageName: String) = "Failed to create run configuration for test from package " + packageName

  override protected def createTestFromLocation(lineNumber: Int, offset: Int, fileName: String): RunnerAndConfigurationSettings = {
    var config: RunnerAndConfigurationSettings = null
    EdtTestUtil.runInEdtAndWait(() => {
      val location = createLocation(lineNumber, offset, fileName)
      val config1 = configurationProducer.createConfigurationFromContextLocation(location)
      config = config1.map(_._2) match {
        case Some(testConfig) => testConfig
        case _ => throw new RuntimeException(failedConfigMessage(fileName, lineNumber, offset))
      }
    })
    config
  }

  override protected def createTestFromPackage(packageName: String): RunnerAndConfigurationSettings =
    createTestFromDirectory(
      ScalaPsiManager.instance(getProject).getCachedPackage(packageName) match {
        case Some(myPackage) => myPackage.getDirectories().head
        case _ => throw new RuntimeException(failedConfigMessage(packageName))
      }
    )

  override protected def createTestFromModule(moduleName: String): RunnerAndConfigurationSettings = {
    val module    = invokeAndWait {
      val manager = ModuleManager.getInstance(ScalaTestingTestCase.this.getProject)
      manager.findModuleByName(moduleName)
    }
    val moduleRoot = ModuleRootManager.getInstance(module).getContentRoots.head
    val directory = PsiDirectoryFactory.getInstance(getProject).createDirectory(moduleRoot)
    createTestFromDirectory(directory)
  }

  private def createTestFromDirectory(directory: PsiDirectory) =
    configurationProducer.createConfigurationFromContextLocation(new PsiLocation(getProject, directory)).map(_._2) match {
      case Some(testConfig) => testConfig
      case _ => throw new RuntimeException(failedConfigMessage(directory.getName))
    }

  override protected def runTestFromConfig(configurationAssert: RunnerAndConfigurationSettings => Unit,
                                           runConfig: RunnerAndConfigurationSettings,
                                           checkOutputs: Boolean = false,
                                           duration: Int = 3000
                                          ): (String, Option[AbstractTestProxy]) = {
    configurationAssert(runConfig)

    assertTrue("runConfig not instance of AbstractRunConfiguration", runConfig.getConfiguration.isInstanceOf[AbstractTestRunConfiguration])
    val testResultListener = new TestResultListener
    val testStatusListener = new TestStatusListener
    var testTreeRoot: Option[AbstractTestProxy] = None

    runConfig.getConfiguration.getProject
      .getMessageBus
      .connect(getTestRootDisposable)
      .subscribe(SMTRunnerEventsListener.TEST_STATUS, testStatusListener)

    val (handler, _) = EdtTestUtil.runInEdtAndGet(() => {
      if (needMake) {
        compiler.rebuild().assertNoProblems(allowWarnings = true)
        saveChecksums()
      }
      val runner = ProgramRunner.PROGRAM_RUNNER_EP.getExtensions.find(_.getClass == classOf[DefaultJavaProgramRunner]).get
      val (handler, runContentDescriptor) = runProcess(runConfig, classOf[DefaultRunExecutor], runner, new ProcessAdapter {
        override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {
          val text = event.getText
          if (debugProcessOutput)
            print(text.replace("##teamcity", "@@teamcity")) // not to display debug test in test console tree
        }
      })

      runContentDescriptor.getExecutionConsole match {
        case descriptor: SMTRunnerConsoleView =>
          testTreeRoot = Some(descriptor.getResultsViewer.getRoot)
        case _ =>
      }
      handler.addProcessListener(testResultListener)
      (handler, runContentDescriptor)
    })

    val outputText = waitForTestEnd(
      runConfig.getName,
      handler,
      testResultListener,
      testStatusListener,
      duration.milliseconds
    )

    (outputText, testTreeRoot)
  }

  private def waitForTestEnd(
    testConfigurationName: String,
    handler: ProcessHandler,
    resultListener: TestResultListener,
    statusListener: TestStatusListener,
    duration: FiniteDuration
  ): String = {
    val exitCode = Try(Await.result(resultListener.exitCodePromise.future, duration))

    // in case of unprocessed output we want to wait for the process end until the project is disposed
    handler.getProcessInput.flush()
    handler.destroyProcess()

    def outputDetails: String = {
      s"""captured outputs:
         |${resultListener.outputText}
         |uncaptured outputs:
         |${statusListener.uncapturedOutput}
         |""".stripMargin
    }

    exitCode match {
      case Success(0) =>
      case Success(code) =>
        fail(
          s"""test `$testConfigurationName` terminated with error exit code: $code;
             |$outputDetails
             |""".stripMargin
        )
      case Failure(exception) =>
        failWithCause(
          s"""test `$testConfigurationName` did not terminate correctly after ${duration.toMillis} ms;
             |$outputDetails""".stripMargin,
          exception
        )
    }
    resultListener.outputTextProgress
  }


  private def runProcess(
    runConfiguration: RunnerAndConfigurationSettings,
    executorClass: Class[_ <: Executor],
    runner: ProgramRunner[_ <: RunnerSettings],
    listener: ProcessListener
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
      handler.addProcessListener(listener)

      processHandler.set(handler)
      contentDescriptor.set(descriptor)

      semaphore.up()
    }

    runner.execute(executionEnvironment)

    semaphore.waitFor()

    (processHandler.get, contentDescriptor.get)
  }
}

object ScalaTestingTestCase {
  def getScalaTestTemplateConfig(project: Project): ScalaTestRunConfiguration =
    ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions.find(_.getId == "ScalaTestRunConfiguration").
      map(_.getConfigurationFactories.head.createTemplateConfiguration(project)).get.asInstanceOf[ScalaTestRunConfiguration]

  def getSpecs2TemplateConfig(project: Project): Specs2RunConfiguration =
    ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions.find(_.getId == "Specs2RunConfiguration").
      map(_.getConfigurationFactories.head.createTemplateConfiguration(project)).get.asInstanceOf[Specs2RunConfiguration]

  def getUTestTemplateConfig(project: Project): UTestRunConfiguration =
    ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions.find(_.getId == "uTestRunConfiguration").
      map(_.getConfigurationFactories.head.createTemplateConfiguration(project)).get.asInstanceOf[UTestRunConfiguration]

}
