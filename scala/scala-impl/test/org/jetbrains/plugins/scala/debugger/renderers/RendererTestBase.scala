package org.jetbrains.plugins.scala.debugger.renderers

import java.util

import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContextImpl}
import com.intellij.debugger.ui.impl.ThreadsDebuggerTree
import com.intellij.debugger.ui.impl.watch.{DebuggerTree, LocalVariableDescriptorImpl, NodeDescriptorImpl}
import com.intellij.debugger.ui.tree._
import com.intellij.debugger.ui.tree.render.{ArrayRenderer, ChildrenBuilder}
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.frame.{XDebuggerTreeNodeHyperlink, XValueChildrenList}
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants
import javax.swing.Icon
import org.jetbrains.plugins.scala.debugger.ScalaDebuggerTestCase

import scala.collection.mutable
import scala.concurrent.duration.{Duration, DurationDouble, FiniteDuration}
import scala.concurrent.{Await, Promise, TimeoutException}
import scala.jdk.CollectionConverters._

/**
 * Nikolay.Tropin
 * 14-Mar-17
 */
abstract class RendererTestBase extends ScalaDebuggerTestCase {

  protected val DefaultTimeout: FiniteDuration = 5.seconds

  protected class MyTinyLogger {
    private val logStart = System.currentTimeMillis()
    private var lastLogTime = logStart
    def debug(text: => String): Unit = {
      return // COMMENT OUT TO SEE METHOD USEFUL DEBUG INFO IN CONSOLE
      val now = System.currentTimeMillis()
      printf("[%5s|%5s] %s\n", now - lastLogTime, now - logStart, text)
      lastLogTime = now
    }
  }

  protected def renderLabelAndChildren(
    variableName: String,
    render: NodeDescriptor => String,
    renderChildren: Boolean,
    childrenCount: Int
  )(implicit timeout: Duration = DefaultTimeout): (String, List[String]) = {
    val log = new MyTinyLogger()

    val frameTree = new ThreadsDebuggerTree(getProject)
    Disposer.register(getTestRootDisposable, frameTree)

    val testVariableEvaluatedPromise: Promise[Unit] = Promise()
    val testVariableChildrenPromise: Promise[Seq[NodeDescriptor]] = {
      if (renderChildren) Promise()
      else Promise.successful(Nil)
    }

    val testVariable = inSuspendContextAction(timeout, "Too long computing variable value") {
      val context = evaluationContext()
      val testVariable = localVar(frameTree, context, variableName)
      testVariable.getRenderer(getDebugProcess).thenApply[Unit] { renderer =>
        testVariable.setRenderer(renderer)
        testVariable.updateRepresentation(context, () => {
          val label = testVariable.getLabel
          log.debug(s"testVariable label changed, name: ${testVariable.getName}, label: $label}")
          val evaluationInProgress = isNodeEvaluating(label)
          if (!evaluationInProgress && !testVariableEvaluatedPromise.isCompleted) {
            log.debug("completing promise")
            testVariableEvaluatedPromise.success(())
          }
        })

        val value = testVariable.calcValue(context)
        if (renderChildren) {
          renderer.buildChildren(value, new DummyChildrenBuilder(frameTree, testVariable) {
            private val result = mutable.LinkedHashSet.empty[DebuggerTreeNode]

            // NOTE: from usages of `setChildren` it looks like it's actually ADD children, not SET
            override def setChildren(children: util.List[_ <: DebuggerTreeNode]): Unit = synchronized {
              val childrenSeq = children.asScala
              result ++= childrenSeq
              result.result()
              log.debug(s"setChildren called, all children $result")
              if (result.size >= childrenCount && !testVariableChildrenPromise.isCompleted) {
                testVariableChildrenPromise.success(result.map(_.getDescriptor).toSeq)
              }
            }
          }, context)
        }
      }

      testVariable
    }

    val testVariableChildren: List[NodeDescriptor] =
      Await.result(testVariableChildrenPromise.future, timeout).toList

    val evaluatedChildren = mutable.HashSet.empty[NodeDescriptor]
    val childrenEvaluatedPromise: Promise[Unit] =
      if (renderChildren) Promise()
      else Promise.successful(())

    testVariableChildren.foreach {
      case child: NodeDescriptorImpl =>
        log.debug(s"updateRepresentation for: ${child.getName}")

        inSuspendContextAction(timeout, s"Too long updating children nodes of $variableName") {
          child.updateRepresentation(evaluationContext(), () => {
            val nodeLabel = child.getLabel
            log.debug(s"child label changed, name: ${child.getName}, label: $nodeLabel}")

            val evaluationInProgress = isNodeEvaluating(nodeLabel)
            if (!evaluationInProgress && !evaluatedChildren.contains(child)) {
              evaluatedChildren += child
              if (testVariableChildren.size == evaluatedChildren.size) {
                childrenEvaluatedPromise.success(())
              }
            }
          })
        }
      case unknown =>
        System.err.println("### UNEXPECTED CHILD TYPE: " + unknown)
    }

    try Await.result(testVariableEvaluatedPromise.future, timeout) catch {
      case ex: TimeoutException =>
        val message = s"Test variable evaluating took too long: ${ex.getMessage}\ncurrent label: ${testVariable.getIdLabel}"
        val error = new AssertionError(message, ex)
        error.setStackTrace(ex.getStackTrace)
        throw error
    }

    try Await.result(childrenEvaluatedPromise.future, timeout) catch {
      case ex: TimeoutException =>
        val availableLabelsText = testVariableChildren
          .map { child => s"name: ${child.getName}, label: ${child.getLabel}" }
          .mkString("\n")
        val message = s"Children nodes evaluating took too long: ${ex.getMessage}\navailable labels:\n$availableLabelsText"
        val error = new AssertionError(message, ex)
        error.setStackTrace(ex.getStackTrace)
        throw error
    }

    //<magic>
    log.debug(s"evalResult($variableName) start")
    // we ignore the result, so don't render self as string (can take some time in some tests, e.g. testQueueWithLongToStringChildren
    val ignoredResult = evalResult(variableName, renderSelfAsString = false)
    log.debug(s"evalResult($variableName) end, result: $ignoredResult")
    //</magic>

    inSuspendContextAction(timeout, s"Too long rendering of $variableName") {
      val variableText = render(testVariable)
      val childrenText = testVariableChildren.map(render)
      (variableText, childrenText)
    }
  }

  private def isNodeEvaluating(nodeLabel: String): Boolean = {
    // i wish there was some more reliable API to check if node is still evaluating =(
    nodeLabel.contains(XDebuggerUIConstants.getCollectingDataMessage)
  }

  protected def localVar(frameTree: DebuggerTree, evaluationContext: EvaluationContextImpl, name: String): LocalVariableDescriptorImpl = {
    try {
      val frameProxy = evaluationContext.getFrameProxy
      val local = frameTree.getNodeFactory.getLocalVariableDescriptor(null, frameProxy visibleVariableByName name)
      local.setContext(evaluationContext)
      local
    } catch {
      case e: EvaluateException => throw e
    }
  }

  private abstract class DummyChildrenBuilder(frameTree: ThreadsDebuggerTree, parentDescriptor: ValueDescriptor) extends ChildrenBuilder {
    override def getDescriptorManager: NodeDescriptorFactory = frameTree.getNodeFactory

    override def getNodeManager: NodeManager = frameTree.getNodeFactory

    override def initChildrenArrayRenderer(renderer: ArrayRenderer, arrayLength: Int): Unit = {}

    override def getParentDescriptor: ValueDescriptor = parentDescriptor

    override def setErrorMessage(errorMessage: String): Unit = {}

    override def setErrorMessage(errorMessage: String, link: XDebuggerTreeNodeHyperlink): Unit = {}

    override def addChildren(children: XValueChildrenList, last: Boolean): Unit = {}

    override def tooManyChildren(remaining: Int): Unit = {}

    override def setMessage(message: String, icon: Icon, attributes: SimpleTextAttributes, link: XDebuggerTreeNodeHyperlink): Unit = {}

    override def setAlreadySorted(alreadySorted: Boolean): Unit = {}

    override def isObsolete: Boolean = false
  }
}
