package org.jetbrains.plugins.scala
package debugger

import java.io.File
import java.util.concurrent.atomic.AtomicReference

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine._
import com.intellij.debugger.engine.evaluation._
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl._
import com.intellij.diagnostic.ThreadDumper
import com.intellij.execution.Executor
import com.intellij.execution.application.{ApplicationConfiguration, ApplicationConfigurationType}
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.{ProcessAdapter, ProcessEvent, ProcessHandler, ProcessListener}
import com.intellij.execution.runners.{ExecutionEnvironmentBuilder, ProgramRunner}
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.{Key, Ref}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.EdtTestUtil
import com.intellij.util.concurrency.Semaphore
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.sun.jdi.VoidValue
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties
import org.jetbrains.plugins.scala.debugger.breakpoints.ScalaLineBreakpointType
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragmentFactory
import org.jetbrains.plugins.scala.extensions._
import org.junit.Assert

/**
 * User: Alefas
 * Date: 13.10.11
 */
abstract class ScalaDebuggerTestCase extends ScalaDebuggerTestBase {

  protected val bp = "<breakpoint>"

  private val breakpoints: mutable.Set[(String, Int, Integer)] = mutable.Set.empty

  //safety net against not running tests at all
  private var wasAtBreakpoint: Boolean = false

  protected def shouldStopAtBreakpointAtLeastOnce(): Boolean = true

  override def setUp(): Unit = {
    super.setUp()
    if (needMake) {
      make()
      saveChecksums()
    }
  }

  protected def runDebugger(mainClass: String = mainClassName, debug: Boolean = false)(callback: => Unit) {
    setupBreakpoints()
    val processHandler = runProcess(mainClass, debug)
    val debugProcess = getDebugProcess
    try {
      callback
    } finally {
      EdtTestUtil.runInEdtAndWait(() => {
        clearXBreakpoints()
        debugProcess.stop(true)
        processHandler.destroyProcess()
      })
    }

    Assert.assertTrue("Stop at breakpoint expected", wasAtBreakpoint || !shouldStopAtBreakpointAtLeastOnce())
  }

  private def runProcess(mainClass: String = mainClassName, debug: Boolean = false): ProcessHandler = {
    val runner = ProgramRunner.PROGRAM_RUNNER_EP.getExtensions.find {
      _.getClass == classOf[GenericDebuggerRunner]
    }.get

    val processHandler = Ref.create[ProcessHandler]
    EdtTestUtil.runInEdtAndWait(() => {
      val handler = runProcess(mainClass, getModule, classOf[DefaultDebugExecutor], new ProcessAdapter {
        override def onTextAvailable(event: ProcessEvent, outputType: Key[_]) {
          val text = event.getText
          if (debug) print(text)
        }
      }, runner)
      processHandler.set(handler)
    })
    processHandler.get
  }

  private def runProcess(className: String,
                         module: Module,
                         executorClass: Class[_ <: Executor],
                         listener: ProcessListener,
                         runner: ProgramRunner[_ <: RunnerSettings]): ProcessHandler = {
    val configuration: ApplicationConfiguration = new ApplicationConfiguration("app", module.getProject, ApplicationConfigurationType.getInstance)
    configuration.setModule(module)
    configuration.setMainClassName(className)
    val executor: Executor = Executor.EXECUTOR_EXTENSION_NAME.findExtension(executorClass)
    val executionEnvironmentBuilder: ExecutionEnvironmentBuilder = new ExecutionEnvironmentBuilder(module.getProject, executor)
    executionEnvironmentBuilder.runProfile(configuration)
    val semaphore: Semaphore = new Semaphore
    semaphore.down()
    val processHandler: AtomicReference[ProcessHandler] = new AtomicReference[ProcessHandler]
    runner.execute(executionEnvironmentBuilder.build, (descriptor: RunContentDescriptor) => {
      val handler: ProcessHandler = descriptor.getProcessHandler
      assert(handler != null)
      handler.addProcessListener(listener)
      processHandler.set(handler)
      semaphore.up()
    })
    semaphore.waitFor()
    processHandler.get
  }

  protected def getDebugProcess: DebugProcessImpl =
    DebuggerManagerEx.getInstanceEx(getProject).getContext.getDebugProcess

  protected def positionManager: ScalaPositionManager = {
    val process = getDebugProcess
    ScalaPositionManager.instance(process).getOrElse {
      new ScalaPositionManager(process)
    }
  }

  protected def resume() {
    val resumeCommand = getDebugProcess.createResumeCommand(currentSuspendContext())
    getDebugProcess.getManagerThread.invokeAndWait(resumeCommand)
  }

  protected def addBreakpoint(line: Int, fileName: String = mainFileName, lambdaOrdinal: Integer = -1) {
    breakpoints += ((fileName, line, lambdaOrdinal))
  }

  protected def clearBreakpoints(): Unit = {
    breakpoints.clear()
    clearXBreakpoints()
  }

  private def setupBreakpoints() {
    invokeAndWaitInTransaction(getProject) {
      breakpoints.foreach {
        case (fileName, line, ordinal) =>
          val ioFile = new File(srcDir, fileName)
          val file = getVirtualFile(ioFile)
          val xBreakpointManager = XDebuggerManager.getInstance(getProject).getBreakpointManager
          val properties = new JavaLineBreakpointProperties
          properties.setLambdaOrdinal(ordinal)
          inWriteAction {
            xBreakpointManager.addLineBreakpoint(scalaLineBreakpointType, file.getUrl, line, properties)
          }
      }
    }
  }

  private def clearXBreakpoints(): Unit = {
    EdtTestUtil.runInEdtAndWait(() => {
      val xBreakpointManager = XDebuggerManager.getInstance(getProject).getBreakpointManager
      inWriteAction {
        xBreakpointManager.getAllBreakpoints.foreach(xBreakpointManager.removeBreakpoint)
      }
    })
  }

  protected def scalaLineBreakpointType = XBreakpointType.EXTENSION_POINT_NAME.findExtension(classOf[ScalaLineBreakpointType])

  protected def waitForBreakpoint(): SuspendContextImpl = {
    val (suspendContext, processTerminated) = waitForBreakpointInner()

    val message =
      if (processTerminated) "process terminated before breakpoint"
      else "too long waiting for breakpoint"

    Assert.assertTrue(message, suspendContext != null)
    Assert.assertTrue("resumed context is not expected on breakpoint", !suspendContext.isResumed)

    suspendContext
  }

  protected def processTerminatedNoBreakpoints(): Boolean = {
    val (_, processTerminated) = waitForBreakpointInner()
    processTerminated
  }

  private def waitForBreakpointInner(): (SuspendContextImpl, Boolean) = {
    val debugProcess = getDebugProcess
    val breakpointSemaphore = new Semaphore()

    val breakpointListener = new DebugProcessAdapterImpl {
      override def paused(suspendContext: SuspendContextImpl): Unit = {
        wasAtBreakpoint = true
        breakpointSemaphore.up()
        debugProcess.removeDebugProcessListener(this)
      }

      override def processDetached(process: DebugProcessImpl, closedByUser: Boolean): Unit = {
        breakpointSemaphore.up()
        debugProcess.removeDebugProcessListener(this)
      }
    }

    managed {
      val ctx = currentSuspendContext()
      if (Option(ctx).forall(_.isResumed)) {
        debugProcess.addDebugProcessListener(breakpointListener)
        breakpointSemaphore.down()
      }
    }
    assertNotManagerThread()

    breakpointSemaphore.waitFor(30000)

    val isAttached = Option(getDebugProcess).exists(_.isAttached)
    (currentSuspendContext(), !isAttached)
  }

  private def assertNotManagerThread(): Unit = {
    Assert.assertTrue("Waiting on manager thread will cause deadlock",
      !DebuggerManagerThreadImpl.isManagerThread)
  }

  protected def currentSuspendContext() = {
    Option(getDebugProcess)
      .map(_.getSuspendManager)
      .flatMap(_.getPausedContext.toOption)
      .orNull
  }

  protected def currentLocation() = managed {
    val suspendContext = currentSuspendContext()
    suspendContext.getFrameProxy.getStackFrame.location
  }

  protected def evaluationContext() = managed {
    new EvaluationContextImpl(currentSuspendContext(), currentSuspendContext().getFrameProxy, currentSuspendContext().getFrameProxy.thisObject())
  }

  protected def currentSourcePosition = managed {
    ContextUtil.getSourcePosition(currentSuspendContext())
  }

  protected def evalResult(codeText: String): String = {
    val ctx = evaluationContext()
    val factory = new ScalaCodeFragmentFactory()
    val factoryWrapper = new CodeFragmentFactoryContextWrapper(factory)
    val evaluatorBuilder: EvaluatorBuilder = factory.getEvaluatorBuilder
    val kind =
      if (codeText.contains("\n")) CodeFragmentKind.CODE_BLOCK
      else CodeFragmentKind.EXPRESSION

    val contextElement = managed {
      ContextUtil.getContextElement(ctx)
    }

    val textWithImports = new TextWithImportsImpl(kind, codeText)
    val fragment = inReadAction {
      val fragment = factoryWrapper.createCodeFragment(textWithImports, contextElement, getProject)
      fragment.forceResolveScope(GlobalSearchScope.allScope(getProject))
      DebuggerUtils.checkSyntax(fragment)
      fragment
    }

    inSuspendContextAction(60.seconds, "Too long evaluate expression: " + codeText) {
      val value = Try {
        val evaluator = inReadAction {
          evaluatorBuilder.build(fragment, currentSourcePosition)
        }
        evaluator.evaluate(ctx)
      }
      value match {
        case Success(v: VoidValue) => "undefined"
        case Success(v) =>
          DebuggerUtils.getValueAsString(ctx, v)
        case Failure(e: EvaluateException) => e.getMessage
        case Failure(e: Throwable) => "Other error: " + e.getMessage
      }
    }
  }

  private def waitScheduledAction[T](timeout: Duration, timeoutMsg: String, callback: => T)
                                    (schedule: (=> Unit) => Unit): T = {
    val result = Ref.create[T]()
    val semaphore = new Semaphore()
    semaphore.down()

    schedule {
      result.set(callback)
      semaphore.up()
    }
    val finished = semaphore.waitFor(timeout.toMillis)
    if (!finished) {
      semaphore.up()
      val messageWithDump =
        s"""$timeoutMsg
           |
           |${ThreadDumper.dumpThreadsToString()}
          """.stripMargin
      Assert.fail(messageWithDump)
    }
    result.get
  }

  protected def inSuspendContextAction[T](timeout: Duration, timeoutMsg: String)(callback: => T): T = {
    val context = currentSuspendContext()
    val process = getDebugProcess

    assertNotManagerThread()

    waitScheduledAction(timeout, timeoutMsg, callback) { body =>
      process.getManagerThread.schedule(new SuspendContextCommandImpl(context) {
        override def contextAction(suspendContext: SuspendContextImpl): Unit = body
      })
    }
  }

  protected def managed[T >: Null](callback: => T): T = {
    if (DebuggerManagerThreadImpl.isManagerThread) callback
    else {
      waitScheduledAction(30.seconds, "Too long debugger action", callback) { body =>
        getDebugProcess.getManagerThread.invoke(() => body)
      }
    }
  }

  protected def evalEquals(codeText: String, expected: String) {
    Assert.assertEquals(s"Evaluating:\n $codeText", expected, evalResult(codeText))
  }

  protected def evalStartsWith(codeText: String, startsWith: String) {
    val result = evalResult(codeText)
    Assert.assertTrue(s"Evaluating:\n $codeText,\n $result doesn't starts with $startsWith",
      result.startsWith(startsWith))
  }

  protected def evaluateCodeFragments(fragmentsWithResults: (String, String)*): Unit = {
    runDebugger() {
      waitForBreakpoint()
      fragmentsWithResults.foreach {
        case (fragment, result) => evalEquals(fragment.stripMargin.trim().replace("\r", ""), result)
      }
    }
  }

  def atNextBreakpoint(action: => Unit): Unit = {
    resume()
    waitForBreakpoint()
    action
  }

  protected def addOtherLibraries(): Unit = {}

  def checkLocation(source: String, methodName: String, lineNumber: Int)(implicit suspendContext: SuspendContextImpl): Unit = {
    def format(s: String, mn: String, ln: Int) = s"$s:$mn:$ln"
    val location = currentLocation()
    val expected = format(source, methodName, lineNumber)
    val actualLine = inReadAction {
      positionManager.getSourcePosition(location).getLine
    }
    val actual = format(location.sourceName, location.method().name(), actualLine + 1)
    Assert.assertEquals("Wrong location:", expected, actual)
  }

  protected def addFileWithBreakpoints(path: String, fileText: String): Unit = {
    val breakpointLines =
      for {
        (line, idx) <- fileText.lines.zipWithIndex
        if line.contains(bp)
      } yield idx
    val cleanedText = fileText.replace(bp, "")
    addSourceFile(path, cleanedText)

    breakpointLines.foreach(addBreakpoint(_, path))
  }
}

case class Loc(className: String, methodName: String, line: Int)