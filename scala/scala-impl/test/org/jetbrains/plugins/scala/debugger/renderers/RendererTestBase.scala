package org.jetbrains.plugins.scala.debugger.renderers

import java.util
import java.util.Collections.emptyList

import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContextImpl}
import com.intellij.debugger.ui.impl.ThreadsDebuggerTree
import com.intellij.debugger.ui.impl.watch.{DebuggerTree, NodeDescriptorImpl}
import com.intellij.debugger.ui.tree._
import com.intellij.debugger.ui.tree.render.{ArrayRenderer, ChildrenBuilder, DescriptorLabelListener}
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.frame.{XDebuggerTreeNodeHyperlink, XValueChildrenList}
import javax.swing.Icon
import org.jetbrains.plugins.scala.debugger.ScalaDebuggerTestCase

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.DurationDouble
import scala.concurrent.{Await, Promise}

/**
 * Nikolay.Tropin
 * 14-Mar-17
 */
abstract class RendererTestBase extends ScalaDebuggerTestCase {

  protected def renderLabelAndChildren(variableName: String,
                                       render: NodeDescriptor => String,
                                       renderChildren: Boolean): (String, List[String]) = {
    val timeout = 5.seconds

    val frameTree = new ThreadsDebuggerTree(getProject)
    Disposer.register(getTestRootDisposable, frameTree)

    val testVariableChildren: Promise[util.List[_ <: DebuggerTreeNode]] = {
      if (renderChildren) Promise()
      else Promise.successful(emptyList)
    }

    val testVariable = inSuspendContextAction(timeout, "Too long computing variable value") {
      val context = evaluationContext()
      val testVariable = localVar(frameTree, context, variableName)
      testVariable.getRenderer(getDebugProcess).thenApply[Unit] { renderer =>
        testVariable.setRenderer(renderer)
        testVariable.updateRepresentation(context, DescriptorLabelListener.DUMMY_LISTENER)

        val value = testVariable.calcValue(context)
        if (renderChildren) {
          renderer.buildChildren(value, new DummyChildrenBuilder(frameTree, testVariable) {
            override def setChildren(children: util.List[_ <: DebuggerTreeNode]): Unit = {
              testVariableChildren.success(children)
            }
          }, context)
        }

      }
      testVariable
    }

    val childrenDescriptors =
      Await.result(testVariableChildren.future, timeout)
        .asScala.map(_.getDescriptor)
        .toList

    childrenDescriptors.foreach {
      case impl: NodeDescriptorImpl =>
        inSuspendContextAction(timeout, s"Too long updating children nodes of $variableName") {
          impl.updateRepresentation(evaluationContext(), DescriptorLabelListener.DUMMY_LISTENER)
        }
      case a => println(a)
    }
    //<magic>
    evalResult(variableName)
    //</magic>

    inSuspendContextAction(timeout, s"Too long rendering of $variableName") {
      (render(testVariable), childrenDescriptors.map(render).toList)
    }
  }

  protected def localVar(frameTree: DebuggerTree, evaluationContext: EvaluationContextImpl, name: String) = {
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
