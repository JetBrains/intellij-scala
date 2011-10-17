package org.jetbrains.plugins.scala.debugger.evaluateExpression

import org.jetbrains.plugins.scala.config.{LibraryLevel, LibraryId, ScalaFacet}
import com.intellij.testFramework.{UsefulTestCase, PsiTestUtil}
import com.intellij.execution.runners.ProgramRunner
import com.intellij.debugger.ui.DebuggerPanelsManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.util.concurrency.Semaphore
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener
import org.jetbrains.plugins.scala.util.{ScalaUtils, TestUtils}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.util.SystemProperties
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.fixtures.ModuleFixture
import com.intellij.execution.process.{ProcessEvent, ProcessAdapter}
import com.intellij.openapi.util.Key
import com.intellij.execution.executors.DefaultDebugExecutor
import org.jetbrains.plugins.scala.ScalaLoader
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import com.intellij.debugger.engine.{ContextUtil, DebuggerUtils, SuspendContextImpl, DebugProcessImpl}
import com.intellij.debugger.impl._
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.sun.jdi.ObjectReference
import junit.framework.Assert
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragmentFactory
import com.intellij.psi.PsiCodeFragment
import com.intellij.debugger.engine.evaluation._
import com.intellij.debugger.{DebuggerBundle, DebuggerManagerEx}
import com.intellij.psi.search.GlobalSearchScope
import expression.{EvaluatorBuilder, EvaluatorBuilderImpl}

/**
 * User: Alefas
 * Date: 13.10.11
 */

abstract class ScalaDebuggerTestCase extends ScalaCompilerTestCase {
  protected override def setUp() {
    UsefulTestCase.edt(new Runnable {
      def run() {
        ScalaDebuggerTestCase.super.setUp()
        ScalaLoader.loadScala()
        SyntheticClasses.get(getProject).registerClasses()
        PsiTestUtil.addLibrary(myModule, "scala-compiler",
          TestUtils.getTestDataPath.replace("\\", "/") + "/scala-compiler/", "scala-compiler.jar",
          "scala-library.jar")

        ScalaUtils.runWriteAction(new Runnable {
          def run() {
            ScalaFacet.createIn(myModule) {
              facet =>
                facet.compilerLibraryId = LibraryId("scala-compiler", LibraryLevel.Project)
            }
          }
        }, getProject, "add faced")
      }
    })
  }


  protected override def tuneFixture(moduleBuilder: JavaModuleFixtureBuilder[_ <: ModuleFixture]) {
    super.tuneFixture(moduleBuilder)
    def javaHome = FileUtil.toSystemIndependentName(SystemProperties.getJavaHome())
    moduleBuilder.addJdk(StringUtil.trimEnd(StringUtil.trimEnd(javaHome, "/"), "/jre"))
  }

  override def runInDispatchThread(): Boolean = false

  override def invokeTestRunnable(runnable: Runnable) {
    runnable.run()
  }
  
  protected def runDebugger(mainClass: String, debug: Boolean = false)(callback: => Unit) {
    make()
    UsefulTestCase.edt(new Runnable {
      def run() {
        val runner = ProgramRunner.PROGRAM_RUNNER_EP.getExtensions.find { _.getClass == classOf[GenericDebuggerRunner] }.get
        runProcess(mainClass, myModule, classOf[DefaultDebugExecutor], new ProcessAdapter {
          override def onTextAvailable(event: ProcessEvent, outputType: Key[_]) {
            val text = event.getText
            if (debug) print(text)
          }
        }, runner)
      }
    })
    callback
    resume()
    assert(getDebugProcess.getExecutionResult.getProcessHandler.waitFor(10000), "too long debugger process")
  }

  private def getDebugProcess: DebugProcessImpl = {
    getDebugSession.getProcess
  }

  private def getDebugSession: DebuggerSession = {
    DebuggerPanelsManager.getInstance(getProject).getSessionTab.getSession
  }

  private def resume() {
    getDebugProcess.getManagerThread.invoke(getDebugProcess.
      createResumeCommand(getDebugProcess.getSuspendManager.getPausedContext))
  }

  protected def addBreakpoint(fileName: String, line: Int) {
    var file: VirtualFile = null
    UsefulTestCase.edt(new Runnable {
      def run() {
        file = myFixture.getTempDirFixture.getFile(fileName)
      }
    })
    addBreakpoint(file, line)
  }

  protected def addBreakpoint(file: VirtualFile,  line: Int) {
    UsefulTestCase.edt(new Runnable {
      def run() {
        DebuggerManagerEx.getInstanceEx(getProject).getBreakpointManager.
          addLineBreakpoint(FileDocumentManager.getInstance().getDocument(file), line)
      }
    })
  }

  protected def waitForBreakpoint(): SuspendContextImpl = {
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

  private def managed[T >: Null](callback: => T): T = {
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

  private def evaluationContext(): EvaluationContextImpl = {
    val suspendContext = getDebugProcess.getSuspendManager.getPausedContext
    new EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy, suspendContext.getFrameProxy.thisObject())
  }

  private def evalResult(codeText: String): String = {
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

      val res = DebuggerUtils.getValueAsString(ctx, evaluator.evaluate(ctx))
      semaphore.up()
      res
    }
    assert(semaphore.waitFor(10000), "Too long evaluate expression: " + codeText)
    result
  }

  protected def evalEquals(codeText: String, expected: String) {
    Assert.assertEquals(evalResult(codeText), expected)
  }

  protected def evalStartsWith(codeText: String, startsWith: String) {
    val result = evalResult(codeText)
    Assert.assertTrue(result + " doesn't strats with " + startsWith,
      result.startsWith(startsWith))
  }
}