package org.jetbrains.plugins.scala
package debugger

import java.io.File
import java.util.concurrent.atomic.AtomicReference

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine._
import com.intellij.debugger.engine.evaluation._
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl._
import com.intellij.execution.Executor
import com.intellij.execution.application.{ApplicationConfiguration, ApplicationConfigurationType}
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.{ProcessAdapter, ProcessEvent, ProcessHandler, ProcessListener}
import com.intellij.execution.runners.{ExecutionEnvironmentBuilder, ProgramRunner}
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.concurrency.Semaphore
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.sun.jdi.VoidValue
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties
import org.jetbrains.plugins.scala.debugger.breakpoints.ScalaLineBreakpointType
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragmentFactory
import org.jetbrains.plugins.scala.extensions._
import org.junit.Assert

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

/**
 * User: Alefas
 * Date: 13.10.11
 */

abstract class ScalaDebuggerTestCase extends ScalaDebuggerTestBase {

  private val breakpoints: mutable.Set[(String, Int, Integer)] = mutable.Set.empty

  protected def runDebugger(mainClass: String, debug: Boolean = false)(callback: => Unit) {
    var processHandler: ProcessHandler = null
    UsefulTestCase.edt(new Runnable {
      def run() {
        if (needMake) {
          make()
          saveChecksums()
        }
        addBreakpoints()
        val runner = ProgramRunner.PROGRAM_RUNNER_EP.getExtensions.find { _.getClass == classOf[GenericDebuggerRunner] }.get
        processHandler = runProcess(mainClass, getModule, classOf[DefaultDebugExecutor], new ProcessAdapter {
          override def onTextAvailable(event: ProcessEvent, outputType: Key[_]) {
            val text = event.getText
            if (debug) print(text)
          }
        }, runner)
      }
    })
    callback
    getDebugProcess.stop(true)
    processHandler.destroyProcess()
  }

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
    executionEnvironmentBuilder.runProfile(configuration)
    val semaphore: Semaphore = new Semaphore
    semaphore.down()
    val processHandler: AtomicReference[ProcessHandler] = new AtomicReference[ProcessHandler]
    runner.execute(executionEnvironmentBuilder.build, new ProgramRunner.Callback {
      def processStarted(descriptor: RunContentDescriptor) {
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

  protected override def tearDown(): Unit = {
    super.tearDown()
  }

  protected def getDebugProcess: DebugProcessImpl = {
    getDebugSession.getProcess
  }

  protected def getDebugSession: DebuggerSession = {
    DebuggerManagerEx.getInstanceEx(getProject).getContext.getDebuggerSession
  }

  private def resume() {
    getDebugProcess.getManagerThread.invoke(getDebugProcess.
        createResumeCommand(getDebugProcess.getSuspendManager.getPausedContext))
  }

  protected def addBreakpoint(fileName: String, line: Int, lambdaOrdinal: Integer = -1) {
    breakpoints += ((fileName, line, lambdaOrdinal))
  }

  private def addBreakpoints() {
    breakpoints.foreach {
      case (fileName, line, ordinal) =>
        val ioFile = new File(srcDir, fileName)
        val file = getVirtualFile(ioFile)
        UsefulTestCase.edt(new Runnable {
          def run() {
            val xBreakpointManager = XDebuggerManager.getInstance(getProject).getBreakpointManager
            val properties = new JavaLineBreakpointProperties
            properties.setLambdaOrdinal(ordinal)
            val breakpointType = XBreakpointType.EXTENSION_POINT_NAME.findExtension(classOf[ScalaLineBreakpointType])
            inWriteAction {
              xBreakpointManager.addLineBreakpoint(breakpointType, file.getUrl, line, properties)
            }
          }
        })
    }
  }

  protected def waitForBreakpoint(): SuspendContextImpl =  {
    var i = 0
    def processTerminated: Boolean = getDebugProcess.getExecutionResult.getProcessHandler.isProcessTerminated
    while (i < 1000 && suspendContext == null && !processTerminated) {
      Thread.sleep(10)
      i += 1
    }

    assert(suspendContext != null, "too long process, terminated=" + processTerminated)
    suspendContext
  }

  protected def managed[T >: Null](callback: => T): T = {
    var result: T = null
    def ctx = DebuggerContextUtil.createDebuggerContext(getDebugSession, suspendContext)
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

  protected def suspendManager = getDebugProcess.getSuspendManager

  protected def suspendContext = suspendManager.getPausedContext

  protected def evaluationContext() = new EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy, suspendContext.getFrameProxy.thisObject())

  protected def currentSourcePosition = ContextUtil.getSourcePosition(suspendContext)

  protected def evalResult(codeText: String): String = {
    val semaphore = new Semaphore()
    semaphore.down()
    val result =
      managed[String] {
        inReadAction {
          val ctx: EvaluationContextImpl = evaluationContext()
          val factory = new ScalaCodeFragmentFactory()
          val codeFragment: PsiCodeFragment = new CodeFragmentFactoryContextWrapper(factory).
              createCodeFragment(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, codeText),
                ContextUtil.getContextElement(ctx), getProject)
          codeFragment.forceResolveScope(GlobalSearchScope.allScope(getProject))
          DebuggerUtils.checkSyntax(codeFragment)
          val evaluatorBuilder: EvaluatorBuilder = factory.getEvaluatorBuilder

          val value = Try {
            val evaluator = evaluatorBuilder.build(codeFragment, currentSourcePosition)
            evaluator.evaluate(ctx)
          }
          val res = value match {
            case Success(v: VoidValue) => "undefined"
            case Success(v) => DebuggerUtils.getValueAsString(ctx, v)
            case Failure(e: EvaluateException) => e.getMessage
          }
          semaphore.up()
          res
        }
      }
    assert(semaphore.waitFor(10000), "Too long evaluate expression: " + codeText)
    result
  }

  protected def evalEquals(codeText: String, expected: String) {
    Assert.assertEquals(expected, evalResult(codeText))
  }

  protected def evalStartsWith(codeText: String, startsWith: String) {
    val result = evalResult(codeText)
    Assert.assertTrue(result + " doesn't starts with " + startsWith,
      result.startsWith(startsWith))
  }

  protected def addOtherLibraries() = {}

  def checkLocation(source: String, methodName: String, lineNumber: Int): Unit = {
    def format(s: String, mn: String, ln: Int) = s"$s:$mn:$ln"
    managed {
      val location = suspendContext.getFrameProxy.getStackFrame.location
      val expected = format(source, methodName, lineNumber)
      val actualLine = inReadAction {
        new ScalaPositionManager(getDebugProcess).getSourcePosition(location).getLine
      }
      val actual = format(location.sourceName, location.method().name(), actualLine + 1)
      Assert.assertEquals("Wrong location:", expected, actual)
    }
  }
}