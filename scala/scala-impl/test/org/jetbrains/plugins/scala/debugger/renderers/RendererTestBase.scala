package org.jetbrains.plugins.scala.debugger.renderers

import java.util
import javax.swing.Icon

import scala.collection.JavaConverters._
import scala.concurrent.duration.DurationDouble

import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContextImpl}
import com.intellij.debugger.ui.impl.ThreadsDebuggerTree
import com.intellij.debugger.ui.impl.watch.{DebuggerTree, NodeDescriptorImpl}
import com.intellij.debugger.ui.tree._
import com.intellij.debugger.ui.tree.render.{ArrayRenderer, ChildrenBuilder, DescriptorLabelListener}
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.frame.{XDebuggerTreeNodeHyperlink, XValueChildrenList}
import org.jetbrains.plugins.scala.debugger.ScalaDebuggerTestCase

/**
  * Nikolay.Tropin
  * 14-Mar-17
  */
abstract class RendererTestBase extends ScalaDebuggerTestCase {

  protected def renderLabelAndChildren(variableName: String, render: NodeDescriptor => String = _.getLabel): (String, List[String]) = {

    val frameTree = new ThreadsDebuggerTree(getProject)
    Disposer.register(getTestRootDisposable, frameTree)
    var testVariableChildren: util.List[DebuggerTreeNode] = null

    val testVariable = inSuspendContextAction(10.seconds, s"Too long rendering of $variableName") {
      val context = evaluationContext()
      val testVariable = localVar(frameTree, context, variableName)
      val renderer = testVariable.getRenderer(getDebugProcess)
      testVariable.setRenderer(renderer)
      testVariable.updateRepresentation(context, DescriptorLabelListener.DUMMY_LISTENER)

      val value = testVariable.calcValue(context)

      renderer.buildChildren(value, new ChildrenBuilder {
        override def setChildren(children: util.List[DebuggerTreeNode]) {testVariableChildren = children}

        override def getDescriptorManager: NodeDescriptorFactory = frameTree.getNodeFactory

        override def getNodeManager: NodeManager = frameTree.getNodeFactory

        override def setRemaining(remaining: Int): Unit = {}

        override def initChildrenArrayRenderer(renderer: ArrayRenderer, arrayLength: Int): Unit = {}

        override def getParentDescriptor: ValueDescriptor = testVariable

        override def setErrorMessage(errorMessage: String): Unit = {}

        override def setErrorMessage(errorMessage: String, link: XDebuggerTreeNodeHyperlink): Unit = {}

        override def addChildren(children: XValueChildrenList, last: Boolean): Unit = {}

        override def tooManyChildren(remaining: Int): Unit = {}

        override def setMessage(message: String, icon: Icon, attributes: SimpleTextAttributes, link: XDebuggerTreeNodeHyperlink): Unit = {}

        override def setAlreadySorted(alreadySorted: Boolean): Unit = {}

        override def isObsolete: Boolean = false
      }, context)

      testVariable
    }

    inSuspendContextAction(10.seconds, s"Too long updating children nodes of $variableName") {
      testVariableChildren.asScala map (_.getDescriptor) foreach {
        case impl: NodeDescriptorImpl =>
          impl.updateRepresentation(evaluationContext(), DescriptorLabelListener.DUMMY_LISTENER)
        case a => println(a)
      }
    }

    //<magic>
    evalResult(variableName)
    //</magic>

    inSuspendContextAction(10.seconds, s"Too long rendering of $variableName") {
      (render(testVariable), testVariableChildren.asScala.map(child => render(child.getDescriptor)).toList)
    }
  }

  protected def localVar(frameTree: DebuggerTree, evaluationContext: EvaluationContextImpl, name: String) = {
    try {
      val frameProxy = evaluationContext.getFrameProxy
      val local = frameTree.getNodeFactory.getLocalVariableDescriptor(null, frameProxy visibleVariableByName name)
      local setContext evaluationContext
      local
    } catch {
      case e: EvaluateException => null
    }
  }
}
