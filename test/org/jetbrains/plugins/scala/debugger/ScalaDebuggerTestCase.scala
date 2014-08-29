package org.jetbrains.plugins.scala
package debugger

import com.intellij.testFramework.{PlatformTestCase, UsefulTestCase}
import com.intellij.execution.runners.{ExecutionEnvironmentBuilder, ProgramRunner}
import com.intellij.debugger.ui.DebuggerPanelsManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.util.concurrency.Semaphore
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import org.jetbrains.plugins.scala.util.TestUtils
import com.intellij.execution.process.{ProcessHandler, ProcessListener, ProcessEvent, ProcessAdapter}
import com.intellij.openapi.util.Key
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.debugger.engine.{ContextUtil, DebuggerUtils, SuspendContextImpl, DebugProcessImpl}
import com.intellij.debugger.impl._
import junit.framework.Assert
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragmentFactory
import com.intellij.psi.PsiCodeFragment
import com.intellij.debugger.engine.evaluation._
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.psi.search.GlobalSearchScope
import expression.EvaluatorBuilder
import com.sun.jdi.VoidValue
import com.intellij.openapi.module.Module
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.application.{ApplicationConfigurationType, ApplicationConfiguration}
import java.util.concurrent.atomic.AtomicReference
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.Disposable
import java.nio.file._
import java.io._
import scala.collection.mutable
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import com.intellij.ide.highlighter.{ModuleFileType, ProjectFileType}
import java.util
import org.jetbrains.plugins.scala.extensions._

/**
 * User: Alefas
 * Date: 13.10.11
 */

abstract class ScalaDebuggerTestCase extends ScalaCompilerTestBase {

  private var needMake = false
  private val checksumsFileName = "checksums.dat"
  private var checksums: mutable.HashMap[String, Array[Byte]] = null
  private val breakpoints: mutable.Set[(String, Int)] = mutable.Set.empty

  override def setUp() {
    needMake = !testDataProjectIsValid()

    UsefulTestCase.edt(new Runnable {
      def run() {
        ScalaDebuggerTestCase.super.setUp()
        inWriteAction {
          addScalaSdk()
        }
      }
    })
  }

  override def setUpModule(): Unit = {
    if (needMake) super.setUpModule()
    else myModule = loadModule(getImlFile)

    PlatformTestCase.myFilesToDelete.remove(getImlFile)
  }

  protected override def tearDown(): Unit = {
    //getDebugSession.dispose()
    super.tearDown()
  }

  override def getIprFile: File = {
    val path = testDataBasePath.resolve(getName + ProjectFileType.DOT_DEFAULT_EXTENSION)
    Files.createDirectories(path.getParent)
    if (!path.toFile.exists()) Files.createFile(path).toFile else path.toFile
  }

  protected def getImlFile: File = {
    val dir = testDataBasePath.toFile
    if (dir.exists()) dir.listFiles().find {_.getName.endsWith(ModuleFileType.DOT_DEFAULT_EXTENSION)}.getOrElse(null)
    else null
  }

  override def runInDispatchThread(): Boolean = false

  protected def runDebugger(mainClass: String, debug: Boolean = false)(callback: => Unit) {
    UsefulTestCase.edt(new Runnable {
      def run() {
        if (needMake) {
          make()
          saveChecksums()
        }
        addBreakpoints()
        val runner = ProgramRunner.PROGRAM_RUNNER_EP.getExtensions.find { _.getClass == classOf[GenericDebuggerRunner] }.get
        runProcess(mainClass, getModule, classOf[DefaultDebugExecutor], new ProcessAdapter {
          override def onTextAvailable(event: ProcessEvent, outputType: Key[_]) {
            val text = event.getText
            if (debug) print(text)
          }
        }, runner)
      }
    })
    callback
    resume()
  }

  override def invokeTestRunnable(runnable: Runnable): Unit = runnable.run()

  protected def runProcess(className: String,
                           module: Module,
                           executorClass: Class[_ <: Executor],
                           listener: ProcessListener,
                           runner: ProgramRunner[_ <: RunnerSettings]): ProcessHandler = {
    val configuration: ApplicationConfiguration = new ApplicationConfiguration("app", module.getProject, ApplicationConfigurationType.getInstance)
    configuration.setModule(module)
    configuration.setMainClassName(className)
    val executor: Executor = Executor.EXECUTOR_EXTENSION_NAME.findExtension(executorClass)
    val executionEnvironmentBuilder: ExecutionEnvironmentBuilder = new ExecutionEnvironmentBuilder(module.getProject, executor)
    executionEnvironmentBuilder.setRunProfile(configuration)
    val semaphore: Semaphore = new Semaphore
    semaphore.down()
    val processHandler: AtomicReference[ProcessHandler] = new AtomicReference[ProcessHandler]
    runner.execute(executionEnvironmentBuilder.build, new ProgramRunner.Callback {
      def processStarted(descriptor: RunContentDescriptor) {
        disposeOnTearDown(new Disposable {
          def dispose() {
            descriptor.dispose()
          }
        })
        val handler: ProcessHandler = descriptor.getProcessHandler
        assert(handler != null)
        handler.addProcessListener(listener)
        processHandler.set(handler)
        semaphore.up()
      }
    })
    semaphore.waitFor()
    processHandler.get
  }

  protected def getDebugProcess: DebugProcessImpl = {
    getDebugSession.getProcess
  }

  protected def getDebugSession: DebuggerSession = {
    DebuggerPanelsManager.getInstance(getProject).getSessionTab.getSession
  }

  private def resume() {
    getDebugProcess.getManagerThread.invoke(getDebugProcess.
      createResumeCommand(getDebugProcess.getSuspendManager.getPausedContext))
  }

  protected def addBreakpoint(fileName: String, line: Int) {
    breakpoints += ((fileName, line))
  }

  private def addBreakpoints() {
    breakpoints.foreach {
      case (fileName, line) =>
        val ioFile = srcDir.toPath.resolve(fileName).toFile
        val file = getVirtualFile(ioFile)
        UsefulTestCase.edt(new Runnable {
          def run() {
            DebuggerManagerEx.getInstanceEx(getProject).getBreakpointManager.
                    addLineBreakpoint(FileDocumentManager.getInstance().getDocument(file), line)
          }
        })
    }
  }

  protected def waitForBreakpoint(): SuspendContextImpl =  {
    var i = 0
    def suspendManager = getDebugProcess.getSuspendManager
    while (i < 1000 && suspendManager.getPausedContext == null && !getDebugProcess.getExecutionResult.getProcessHandler.isProcessTerminated) {
      Thread.sleep(10)
      i += 1
    }

    def context = suspendManager.getPausedContext
    assert(context != null, "too long process, terminated=" +
      getDebugProcess.getExecutionResult.getProcessHandler.isProcessTerminated)
    context
  }

  protected def managed[T >: Null](callback: => T): T = {
    var result: T = null
    def ctx = DebuggerContextUtil.createDebuggerContext(getDebugSession, getDebugProcess.getSuspendManager.getPausedContext)
    val semaphore = new Semaphore()
    semaphore.down()
    getDebugProcess.getManagerThread.invokeAndWait(new DebuggerContextCommandImpl(ctx) {
      def threadAction() {
        result = callback
        semaphore.up()
      }
    })
    def finished = semaphore.waitFor(20000)
    assert(finished, "Too long debugger action")
    result
  }

  protected def evaluationContext(): EvaluationContextImpl = {
    val suspendContext = getDebugProcess.getSuspendManager.getPausedContext
    new EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy, suspendContext.getFrameProxy.thisObject())
  }

  protected def evalResult(codeText: String): String = {
    val semaphore = new Semaphore()
    semaphore.down()
    val result = managed[String] {
      val ctx: EvaluationContextImpl = evaluationContext()
      val factory = new ScalaCodeFragmentFactory()
      val codeFragment: PsiCodeFragment = new CodeFragmentFactoryContextWrapper(factory).
        createCodeFragment(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, codeText),
        ContextUtil.getContextElement(ctx), getProject)
      codeFragment.forceResolveScope(GlobalSearchScope.allScope(getProject))
      DebuggerUtils.checkSyntax(codeFragment)
      val evaluatorBuilder: EvaluatorBuilder = factory.getEvaluatorBuilder
      val evaluator = evaluatorBuilder.build(codeFragment, ContextUtil.getSourcePosition(ctx))

      val value = evaluator.evaluate(ctx)
      val res = value match {
        case v: VoidValue => "undefined"
        case _ => DebuggerUtils.getValueAsString(ctx, value)
      }
      semaphore.up()
      res
    }
    assert(semaphore.waitFor(10000), "Too long evaluate expression: " + codeText)
    result
  }

  protected def evalEquals(codeText: String, expected: String) {
    Assert.assertEquals(expected, evalResult(codeText))
  }

  protected def evalStartsWith(codeText: String, startsWith: String) {
    val result = evalResult(codeText)
    Assert.assertTrue(result + " doesn't strats with " + startsWith,
      result.startsWith(startsWith))
  }

  override protected def addFileToProject(relPath: String, fileText: String) {
    val srcPath = Paths.get("src", relPath)
    if (needMake || !checkSourceFile(srcPath, fileText)) {
      needMake = true
      val file = testDataBasePath.resolve(srcPath).toFile
      if (file.exists()) file.delete()
      super.addFileToProject(relPath, fileText)
    }
  }

  private def testDataBasePath: Path = {
    val testClassName = this.getClass.getSimpleName.stripSuffix("Test")
    val path = FileSystems.getDefault.getPath(TestUtils.getTestDataPath, "debugger", testClassName, getTestName(true))
    if (path.toFile.exists()) path
    else Files.createDirectories(path)
  }

  def getVirtualFile(file: File) = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(file)

  def md5(file: File): Array[Byte] = {
    val md = MessageDigest.getInstance("MD5")
    val isSource = file.getName.endsWith(".java") || file.getName.endsWith(".scala")
    if (isSource) {
      val text = scala.io.Source.fromFile(file, "UTF-8").mkString.replace("\r", "")
      md.digest(text.getBytes(StandardCharsets.UTF_8))
    } else {
      md.digest(Files.readAllBytes(file.toPath))
    }
  }
  
  private def computeChecksums(): mutable.HashMap[String, Array[Byte]] = {
    val result = new mutable.HashMap[String, Array[Byte]]
    def computeForDir(dir: File) {
      if (dir.exists) dir.listFiles().foreach { f =>
        if (f.isDirectory) computeForDir(f)
        else result += (testDataBasePath.relativize(f.toPath).toString -> md5(f))
      }
    }
    computeForDir(srcDir)
    computeForDir(outDir)
    result
  }


  protected def outDir: File = testDataBasePath.resolve("out").toFile

  protected def srcDir: File = testDataBasePath.resolve("src").toFile

  private def saveChecksums() = {
    checksums = computeChecksums()
    val file = testDataBasePath.resolve(checksumsFileName).toFile
    if (!file.exists) Files.createFile(file.toPath)
    val oos = new ObjectOutputStream(new FileOutputStream(file))
    try {
      oos.writeObject(checksums)
    }
    finally {
      oos.close()
    }
  }

  private def loadChecksums(): Unit = {
    val file = testDataBasePath.resolve(checksumsFileName).toFile
    if (!file.exists) {
      needMake = true
      return
    }
    val ois = new ObjectInputStream(new FileInputStream(file))
    try {
      val obj = ois.readObject()
      obj match {
        case map: mutable.HashMap[String, Array[Byte]] => checksums = map
        case _ => needMake = true
      }
    }
    finally ois.close()
  }

  private def testDataProjectIsValid(): Boolean = {
    loadChecksums()
    !needMake && checksums.keys.forall(checkFile) && getImlFile != null
  }

  private def checkSourceFile(relPath: Path, fileText: String): Boolean = {
    val file = testDataBasePath.resolve(relPath).toFile
    val oldText = scala.io.Source.fromFile(file, "UTF-8").mkString
    oldText.replace("\r", "") == fileText.replace("\r", "")
  }
  
  private def checkFile(relPath: String): Boolean = {
    val file = testDataBasePath.resolve(relPath).toFile
    file.exists && util.Arrays.equals(checksums(relPath), md5(file))
  }
}