package org.jetbrains.plugins.scala.debugger
package renderers

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.jdi.LocalVariablesUtil
import com.intellij.debugger.ui.impl.ThreadsDebuggerTree
import com.intellij.debugger.ui.impl.watch.{DebuggerTree, LocalVariableDescriptorImpl, ValueDescriptorImpl}
import com.intellij.debugger.ui.tree.render.{ArrayRenderer, ChildrenBuilder, NodeRenderer}
import com.intellij.debugger.ui.tree.{DebuggerTreeNode, NodeDescriptorFactory, NodeManager, ValueDescriptor}
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.frame.{XDebuggerTreeNodeHyperlink, XValueChildrenList}
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants
import com.sun.jdi.Value
import org.jetbrains.plugins.scala.debugger.ui.util._

import java.util.concurrent.{CompletableFuture, TimeUnit}
import javax.swing.Icon
import scala.collection.mutable
import scala.jdk.CollectionConverters._

abstract class RendererTestBase extends NewScalaDebuggerTestCase {

  protected def rendererTest(className: String = getTestName(false))(test: SuspendContextImpl => Unit): Unit = {
    createLocalProcess(className)

    doWhenXSessionPausedThenResume { () =>
      val context = getDebugProcess.getDebuggerContext.getSuspendContext
      test(context)
    }
  }

  protected def renderLabelAndChildren(name: String,
                                       renderChildren: Boolean,
                                       findVariable: (DebuggerTree, EvaluationContextImpl, String) => ValueDescriptorImpl = localVar)
                                      (implicit context: SuspendContextImpl): (String, Seq[String]) = {
    val frameTree = new ThreadsDebuggerTree(getProject)
    Disposer.register(getTestRootDisposable, frameTree)

    (for {
      ec <- onDebuggerManagerThread(context)(createEvaluationContext(context))
      variable <- onDebuggerManagerThread(context)(findVariable(frameTree, ec, name))
      label <- renderLabel(variable, ec)
      value <- onDebuggerManagerThread(context)(variable.getValue)
      renderer <- onDebuggerManagerThread(context)(variable.getRenderer(context.getDebugProcess)).flatten
      children <- if (renderChildren) buildChildren(value, frameTree, variable, renderer, ec) else CompletableFuture.completedFuture(Seq.empty)
      childrenLabels <- children.map(renderLabel(_, ec)).sequence
    } yield (label, childrenLabels)).get(3L, TimeUnit.MINUTES)
  }

  private def buildChildren(value: Value,
                            frameTree: ThreadsDebuggerTree,
                            descriptor: ValueDescriptorImpl,
                            renderer: NodeRenderer,
                            context: EvaluationContextImpl): CompletableFuture[Seq[ValueDescriptorImpl]] = {
    val future = new CompletableFuture[Seq[ValueDescriptorImpl]]()

    onDebuggerManagerThread(context) {
      renderer.buildChildren(value, new DummyChildrenBuilder(frameTree, descriptor) {
        private val allChildren = mutable.ListBuffer.empty[DebuggerTreeNode]

        override def addChildren(children: java.util.List[_ <: DebuggerTreeNode], last: Boolean): Unit = {
          allChildren ++= children.asScala
          if (last && !future.isDone) {
            val result = allChildren.map(_.getDescriptor).collect { case n: ValueDescriptorImpl => n }.toSeq
            future.complete(result)
          }
        }
      }, context)
    }

    future
  }

  private def renderLabel(descriptor: ValueDescriptorImpl, context: EvaluationContextImpl): CompletableFuture[String] = {
    val asyncLabel = new CompletableFuture[String]()

    onDebuggerManagerThread(context)(descriptor.updateRepresentationNoNotify(context, () => {
      val label = descriptor.getLabel
      if (labelCalculated(label)) {
        asyncLabel.complete(label)
      }
    }))

    asyncLabel
  }

  private def labelCalculated(label: String): Boolean =
    !label.contains(XDebuggerUIConstants.getCollectingDataMessage) && label.split(" = ").lengthIs >= 2

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

    override def setChildren(children: java.util.List[_ <: DebuggerTreeNode]): Unit = {
      addChildren(children, true)
    }

    //noinspection ScalaDeprecation
    override def tooManyChildren(remaining: Int): Unit = {}

    override def setMessage(message: String, icon: Icon, attributes: SimpleTextAttributes, link: XDebuggerTreeNodeHyperlink): Unit = {}

    override def setAlreadySorted(alreadySorted: Boolean): Unit = {}

    override def isObsolete: Boolean = false
  }
}
