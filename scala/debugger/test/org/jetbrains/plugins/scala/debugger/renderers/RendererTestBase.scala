package org.jetbrains.plugins.scala
package debugger
package renderers

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.engine.{DebuggerManagerThreadImpl, JavaValue, SuspendContextImpl}
import com.intellij.debugger.ui.impl.watch.{NodeManagerImpl, ValueDescriptorImpl}
import com.intellij.debugger.ui.tree.render.{ArrayRenderer, ChildrenBuilder, NodeRenderer}
import com.intellij.debugger.ui.tree.{DebuggerTreeNode, NodeDescriptorFactory, NodeManager, ValueDescriptor}
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.frame.{XDebuggerTreeNodeHyperlink, XValueChildrenList}
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants
import org.jetbrains.plugins.scala.debugger.ui.util._

import java.util.Collections
import java.util.concurrent.{CompletableFuture, TimeUnit}
import javax.swing.Icon
import scala.collection.mutable
import scala.jdk.CollectionConverters._

abstract class RendererTestBase extends ScalaDebuggerTestCase {

  protected def rendererTest(className: String = getTestName(false))(test: SuspendContextImpl => Unit): Unit = {
    createLocalProcess(className)

    doWhenXSessionPausedThenResume { () =>
      val context = getDebugProcess.getDebuggerContext.getSuspendContext
      test(context)
    }
  }

  protected def renderLabelAndChildren(name: String, renderChildren: Boolean)
                                       (implicit context: SuspendContextImpl): (String, Seq[String]) = {
    (for {
      variable <- variableToRender(name)
      label <- renderLabel(variable.descriptor, variable.context)
      children <- if (renderChildren) buildChildren(variable) else CompletableFuture.completedFuture(Seq.empty)
      childrenLabels <- children.map(renderLabel(_, variable.context)).sequence
    } yield (label, childrenLabels)).get(3L, TimeUnit.MINUTES)
  }

  /**
   * Expands the variable `name` and returns the error message that its node shows instead of the children.
   */
  protected def renderChildrenError(name: String)(implicit context: SuspendContextImpl): String = {
    val result = (for {
      variable <- variableToRender(name)
      children <- buildChildren(variable).attempt
    } yield children).get(3L, TimeUnit.MINUTES)

    result match {
      case Left(failure) => DebuggerUtilsAsync.unwrap(failure) match {
        case ChildrenError(message) => message
        case other => throw other
      }
      case Right(children) =>
        throw new AssertionError(s"The children of $name were built instead of an error: ${children.map(_.getName).mkString(", ")}")
    }
  }

  private def variableToRender(name: String)(implicit context: SuspendContextImpl): CompletableFuture[VariableToRender] =
    for {
      nodeManager <- onDebuggerManagerThread(context)(context.getDebugProcess.getXdebugProcess.getNodeManager)
      variable <- onDebuggerManagerThread(context)(context.getFrameProxy.visibleVariableByName(name))
      frame <- onDebuggerManagerThread(context)(context.getActiveExecutionStack.getTopFrame.asInstanceOf[ScalaStackFrame])
      children = new XValueChildrenList()
      ec <- onDebuggerManagerThread(context)(createEvaluationContext(context))
      _ <- onDebuggerManagerThread(context)(frame.buildLocalVariables(ec, children, Collections.singletonList(variable)))
      descriptor = children.getValue(0).asInstanceOf[JavaValue].getDescriptor
      _ <- onDebuggerManagerThread(context)(descriptor.setContext(ec))
      renderer <- onDebuggerManagerThread(context)(context.getDebugProcess.getAutoRendererAsync(descriptor.getType)).flatten
    } yield VariableToRender(descriptor, renderer, nodeManager, ec)

  /**
   * Completes with the children of the variable, or fails with a [[ChildrenError]] if its node shows an error instead.
   */
  private def buildChildren(variable: VariableToRender): CompletableFuture[Seq[ValueDescriptorImpl]] = {
    val future = new CompletableFuture[Seq[ValueDescriptorImpl]]()
    val VariableToRender(descriptor, renderer, nodeManager, context) = variable

    onDebuggerManagerThread(context) {
      renderer.buildChildren(descriptor.getValue, new DummyChildrenBuilder(nodeManager, descriptor) {
        private val allChildren = mutable.ListBuffer.empty[DebuggerTreeNode]

        override def addChildren(children: java.util.List[? <: DebuggerTreeNode], last: Boolean): Unit = {
          allChildren ++= children.asScala
          if (last && !future.isDone) {
            val result = allChildren.map(_.getDescriptor).collect { case n: ValueDescriptorImpl => n }.toSeq
            result.foreach(_.setContext(context))
            future.complete(result)
          }
        }

        override def setErrorMessage(errorMessage: String): Unit =
          future.completeExceptionally(ChildrenError(errorMessage))

        // loads the next batch of children right away, like a click on the "..." node
        override def tooManyChildren(remaining: Int, addNextChildren: Runnable): Unit =
          addNextChildren.run()
      }, context)
    }

    future
  }

  private def renderLabel(descriptor: ValueDescriptorImpl, context: EvaluationContextImpl): CompletableFuture[String] = {
    val asyncLabel = new CompletableFuture[String]()

    onDebuggerManagerThread(context)(descriptor.updateRepresentationNoNotify(context, () => {
      val label =
        if (DebuggerManagerThreadImpl.isManagerThread) descriptor.getLabel
        else onDebuggerManagerThread(context)(descriptor.getLabel).get()

      if (labelCalculated(label)) {
        asyncLabel.complete(label)
      }
    }))

    asyncLabel
  }

  private def labelCalculated(label: String): Boolean =
    !label.contains(XDebuggerUIConstants.getCollectingDataMessage) && label.split(" = ").lengthIs >= 2

  private abstract class DummyChildrenBuilder(nodeManager: NodeManagerImpl, parentDescriptor: ValueDescriptor) extends ChildrenBuilder {
    override def getDescriptorManager: NodeDescriptorFactory = nodeManager

    override def getNodeManager: NodeManager = nodeManager

    override def initChildrenArrayRenderer(renderer: ArrayRenderer, arrayLength: Int): Unit = {}

    override def getParentDescriptor: ValueDescriptor = parentDescriptor

    override def setErrorMessage(errorMessage: String): Unit = {}

    override def setErrorMessage(errorMessage: String, link: XDebuggerTreeNodeHyperlink): Unit = setErrorMessage(errorMessage)

    override def addChildren(children: XValueChildrenList, last: Boolean): Unit = {}

    override def setChildren(children: java.util.List[? <: DebuggerTreeNode]): Unit = {
      addChildren(children, true)
    }

    //noinspection ScalaDeprecation
    override def tooManyChildren(remaining: Int): Unit = {}

    override def setMessage(message: String, icon: Icon, attributes: SimpleTextAttributes, link: XDebuggerTreeNodeHyperlink): Unit = {}

    override def setAlreadySorted(alreadySorted: Boolean): Unit = {}

    override def isObsolete: Boolean = false
  }

  private final case class VariableToRender(descriptor: ValueDescriptorImpl,
                                            renderer: NodeRenderer,
                                            nodeManager: NodeManagerImpl,
                                            context: EvaluationContextImpl)

  private final case class ChildrenError(message: String) extends Exception(s"The node shows an error instead of the children: $message")
}
