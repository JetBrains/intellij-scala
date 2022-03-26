package org.jetbrains.plugins.scala.debugger.renderers

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.jdi.LocalVariablesUtil
import com.intellij.debugger.ui.impl.ThreadsDebuggerTree
import com.intellij.debugger.ui.impl.watch.{DebuggerTree, LocalVariableDescriptorImpl, NodeDescriptorImpl, ValueDescriptorImpl}
import com.intellij.debugger.ui.tree.render.{ArrayRenderer, ChildrenBuilder, NodeRenderer}
import com.intellij.debugger.ui.tree.{DebuggerTreeNode, NodeDescriptorFactory, NodeManager, ValueDescriptor}
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.frame.{XDebuggerTreeNodeHyperlink, XValueChildrenList}
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants
import com.sun.jdi.Value
import org.jetbrains.plugins.scala.debugger.ScalaDebuggerTestCase
import org.jetbrains.plugins.scala.debugger.ui.util._

import java.util.concurrent.CompletableFuture
import javax.swing.Icon
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

abstract class RendererTestBase extends ScalaDebuggerTestCase {

  protected implicit val DefaultTimeout: Duration = 3.minutes

  protected def renderLabelAndChildren(name: String, childrenCount: Option[Int], findVariable: (DebuggerTree, EvaluationContextImpl, String) => ValueDescriptorImpl = localVar)(implicit timeout: Duration): (String, Seq[String]) = {
    val frameTree = new ThreadsDebuggerTree(getProject)
    Disposer.register(getTestRootDisposable, frameTree)
    val context = evaluationContext()

    (for {
      variable <- onDebuggerManagerThread(context)(findVariable(frameTree, context, name))
      label <- renderLabel(variable, context)
      value <- onDebuggerManagerThread(context)(variable.calcValue(context))
      renderer <- onDebuggerManagerThread(context)(variable.getRenderer(context.getDebugProcess)).flatten
      children <- childrenCount.map(c => buildChildren(value, frameTree, variable, renderer, context, c)).getOrElse(CompletableFuture.completedFuture(Seq.empty))
      childrenLabels <- children.map(renderLabel(_, context)).sequence
    } yield (label, childrenLabels)).get(timeout.length, timeout.unit)
  }

  private def buildChildren(value: Value,
                            frameTree: ThreadsDebuggerTree,
                            descriptor: ValueDescriptorImpl,
                            renderer: NodeRenderer,
                            context: EvaluationContextImpl,
                            count: Int): CompletableFuture[Seq[NodeDescriptorImpl]] = {
    val future = new CompletableFuture[Seq[NodeDescriptorImpl]]()

    onDebuggerManagerThread(context) {
      renderer.buildChildren(value, new DummyChildrenBuilder(frameTree, descriptor) {
        private val allChildren = new java.util.ArrayList[DebuggerTreeNode]()

        override def setChildren(children: java.util.List[_ <: DebuggerTreeNode]): Unit = {
          allChildren.addAll(children)
          if (allChildren.size() == count && !future.isDone) {
            val result = allChildren.asScala.map(_.getDescriptor).collect { case n: NodeDescriptorImpl => n }.toSeq
            future.complete(result)
          }
        }
      }, context)
    }

    future
  }

  private def renderLabel(descriptor: NodeDescriptorImpl, context: EvaluationContextImpl): CompletableFuture[String] = {
    val future = new CompletableFuture[String]()

    for {
      _ <- onDebuggerManagerThread(context) {
        descriptor.updateRepresentation(context, () => {
          val label = descriptor.getLabel
          val inProgress = isNodeEvaluating(label)
          if (!inProgress && !future.isDone) {
            future.complete(label)
          }
        })
      }
    } yield ()

    future
  }

  private def isNodeEvaluating(label: String): Boolean =
    label.contains(XDebuggerUIConstants.getCollectingDataMessage) ||
      label.split(" = ").lengthIs <= 1

  protected def localVar(frameTree: DebuggerTree, context: EvaluationContextImpl, name: String): LocalVariableDescriptorImpl = {
    val frameProxy = context.getFrameProxy
    val local = frameTree.getNodeFactory.getLocalVariableDescriptor(null, frameProxy.visibleVariableByName(name))
    local.setContext(context)
    local
  }

  protected def parameter(index: Int)(frameTree: DebuggerTree, context: EvaluationContextImpl, name: String): ValueDescriptorImpl = {
    val _ = name
    val frameProxy = context.getFrameProxy
    val mapping = LocalVariablesUtil.fetchValues(frameProxy, context.getDebugProcess, true)
    val (dv, v) = mapping.asScala.toList(index)
    val param = frameTree.getNodeFactory.getArgumentValueDescriptor(null, dv, v)
    param.setContext(context)
    param
  }

  private abstract class DummyChildrenBuilder(frameTree: ThreadsDebuggerTree, parentDescriptor: ValueDescriptor) extends ChildrenBuilder {
    override def getDescriptorManager: NodeDescriptorFactory = frameTree.getNodeFactory

    override def getNodeManager: NodeManager = frameTree.getNodeFactory

    override def initChildrenArrayRenderer(renderer: ArrayRenderer, arrayLength: Int): Unit = {}

    override def getParentDescriptor: ValueDescriptor = parentDescriptor

    override def setErrorMessage(errorMessage: String): Unit = {}

    override def setErrorMessage(errorMessage: String, link: XDebuggerTreeNodeHyperlink): Unit = {}

    override def addChildren(children: XValueChildrenList, last: Boolean): Unit = {}

    //noinspection ScalaDeprecation
    override def tooManyChildren(remaining: Int): Unit = {}

    override def setMessage(message: String, icon: Icon, attributes: SimpleTextAttributes, link: XDebuggerTreeNodeHyperlink): Unit = {}

    override def setAlreadySorted(alreadySorted: Boolean): Unit = {}

    override def isObsolete: Boolean = false
  }
}
