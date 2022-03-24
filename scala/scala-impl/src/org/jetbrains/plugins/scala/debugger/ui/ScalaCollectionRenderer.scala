package org.jetbrains.plugins.scala.debugger
package ui

import com.intellij.debugger.engine.evaluation.expression.{Evaluator, ExpressionEvaluator, ExpressionEvaluatorImpl, IdentityEvaluator}
import com.intellij.debugger.engine.evaluation.{EvaluationContext, EvaluationContextImpl}
import com.intellij.debugger.engine.{DebuggerUtils, JVMNameUtil}
import com.intellij.debugger.ui.tree.render.{ChildrenBuilder, DescriptorLabelListener}
import com.intellij.debugger.ui.tree.{NodeDescriptor, ValueDescriptor}
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants
import com.sun.jdi._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.{ScalaFieldEvaluator, ScalaMethodEvaluator, ScalaTypeEvaluator}
import org.jetbrains.plugins.scala.debugger.filters.ScalaDebuggerSettings
import org.jetbrains.plugins.scala.debugger.ui.util._

import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters._

class ScalaCollectionRenderer extends ScalaClassRenderer {

  import ScalaCollectionRenderer._

  override def getName: String = ScalaBundle.message("scala.collection.renderer")

  override def isApplicableFor(tpe: Type): Boolean =
    tpe match {
      case ct: ClassType if isCollection(ct) => true
      case _ => false
    }

  override def isEnabled: Boolean =
    ScalaDebuggerSettings.getInstance().FRIENDLY_COLLECTION_DISPLAY_ENABLED

  override def isExpandableAsync(value: Value, context: EvaluationContext, parentDescriptor: NodeDescriptor): CompletableFuture[java.lang.Boolean] = {
    val ref = value.asInstanceOf[ObjectReference]
    onDebuggerManagerThread(context)(evaluateNonEmpty(ref, context))
  }

  override def calcLabel(descriptor: ValueDescriptor, context: EvaluationContext, listener: DescriptorLabelListener): String = {
    val ref = descriptor.getValue.asInstanceOf[ObjectReference]

    def computeSize(hasDefiniteSize: Boolean): CompletableFuture[String] =
      if (hasDefiniteSize) onDebuggerManagerThread(context)(evaluateSize(ref, context)).map(_.toString)
      else CompletableFuture.completedFuture("?")

    val sizeLabel = for {
      hasDefiniteSize <- onDebuggerManagerThread(context)(evaluateHasDefiniteSize(ref, context))
      size <- computeSize(hasDefiniteSize)
    } yield s"size = $size"

    sizeLabel.attempt.flatTap {
      case Left(t) =>
        onDebuggerManagerThread(context) {
          descriptor.setValueLabelFailed(evaluationExceptionFromThrowable(t))
          listener.labelChanged()
        }
      case Right(label) =>
        onDebuggerManagerThread(context) {
          descriptor.setValueLabel(label)
          listener.labelChanged()
        }
    }.rethrow.getNow(XDebuggerUIConstants.getCollectingDataMessage)
  }

  override def buildChildren(value: Value, builder: ChildrenBuilder, context: EvaluationContext): Unit = {
    val ref = value.asInstanceOf[ObjectReference]
    val project = context.getProject
    val nodeManager = builder.getNodeManager

    for {
      taken <- onDebuggerManagerThread(context)(evaluateTake(ref, 100, context))
      array <- onDebuggerManagerThread(context)(evaluateToArray(taken, context))
      _ <- onDebuggerManagerThread(context) {
        val elements = allValues(array)
        val descs = elements.zipWithIndex.map { case (v, i) =>
          val desc = new CollectionElementDescriptor(project, i, v)
          nodeManager.createNode(desc, context)
        }
        builder.addChildren(descs.asJava, false)
      }
      hasDefiniteSize <- onDebuggerManagerThread(context)(evaluateHasDefiniteSize(ref, context))
      size <- if (hasDefiniteSize) onDebuggerManagerThread(context)(evaluateSize(ref, context)).map(Some(_)) else CompletableFuture.completedFuture(None)
      _ <- onDebuggerManagerThread(context) {
        val remaining = size.map(n => math.max(n - array.length(), 0))
        remaining match {
          case Some(0) => builder.addChildren(List.empty.asJava, true)
          case _ =>
            val desc = new ExpandCollectionDescriptor(project, 100, ref, remaining, builder)
            val node = nodeManager.createNode(desc, context)
            builder.addChildren(List(node).asJava, true)
        }
      }
    } yield ()
  }
}

private object ScalaCollectionRenderer {
  private def isCollection(ct: ClassType): Boolean =
    DebuggerUtils.instanceOf(ct, "scala.collection.Iterable")

  def isNonStrictCollection(ct: ClassType): Boolean = {
    def isView: Boolean =
      DebuggerUtils.instanceOf(ct, "scala.collection.View") ||
        DebuggerUtils.instanceOf(ct, "scala.collection.IterableView")

    def isLazyList: Boolean =
      DebuggerUtils.instanceOf(ct, "scala.collection.immutable.LazyList") ||
        DebuggerUtils.instanceOf(ct, "scala.collection.immutable.Stream")

    isCollection(ct: ClassType) && (isLazyList || isView)
  }

  def evaluateHasDefiniteSize(ref: ObjectReference, context: EvaluationContext): Boolean =
    ScalaMethodEvaluator(
      new IdentityEvaluator(ref),
      "hasDefiniteSize",
      JVMNameUtil.getJVMRawText("()Z"),
      Seq.empty
    ).asExpressionEvaluator.evaluate(context).asInstanceOf[BooleanValue].value()

  def evaluateSize(ref: ObjectReference, context: EvaluationContext): Int =
    ScalaMethodEvaluator(
      new IdentityEvaluator(ref),
      "size",
      JVMNameUtil.getJVMRawText("()I"),
      Seq.empty
    ).asExpressionEvaluator.evaluate(context).asInstanceOf[IntegerValue].value()

  private def evaluateNonEmpty(ref: ObjectReference, context: EvaluationContext): Boolean =
    ScalaMethodEvaluator(
      new IdentityEvaluator(ref),
      "nonEmpty",
      JVMNameUtil.getJVMRawText("()Z"),
      Seq.empty
    ).asExpressionEvaluator.evaluate(context).asInstanceOf[BooleanValue].value()

  private def evaluateIterableOperation(name: String)(ref: ObjectReference, n: Int, context: EvaluationContext): ObjectReference =
    ScalaMethodEvaluator(
      new IdentityEvaluator(ref),
      name,
      JVMNameUtil.getJVMRawText("(I;)Lscala/collection/Iterable"),
      Seq(new IntEvaluator(n))
    ).asExpressionEvaluator.evaluate(context).asInstanceOf[ObjectReference]

  def evaluateDrop(ref: ObjectReference, n: Int, context: EvaluationContext): ObjectReference =
    evaluateIterableOperation("drop")(ref, n, context)

  def evaluateTake(ref: ObjectReference, n: Int, context: EvaluationContext): ObjectReference =
    evaluateIterableOperation("take")(ref, n, context)

  def evaluateToArray(ref: ObjectReference, context: EvaluationContext): ArrayReference =
    ScalaMethodEvaluator(
      new IdentityEvaluator(ref),
      "toArray",
      JVMNameUtil.getJVMRawText("(scala.reflect.ClassTag;)[Ljava/lang/Object"),
      Seq(ObjectClassTagEvaluator)
    ).asExpressionEvaluator.evaluate(context).asInstanceOf[ArrayReference]

  def allValues(arr: ArrayReference): Vector[Value] =
    arr.getValues.asScala.toVector

  private[this] val ObjectClassTagEvaluator: ScalaMethodEvaluator = {
    val classTagName = JVMNameUtil.getJVMRawText("scala.reflect.ClassTag$")
    val module = ScalaFieldEvaluator(new ScalaTypeEvaluator(classTagName), "MODULE$")
    ScalaMethodEvaluator(module, "Object", JVMNameUtil.getJVMRawText("()Lscala/reflect/ClassTag"), Seq.empty)
  }

  private[this] implicit class EvaluatorToExpressionEvaluatorOps(private val evaluator: Evaluator) extends AnyVal {
    def asExpressionEvaluator: ExpressionEvaluator = new ExpressionEvaluatorImpl(evaluator)
  }

  private[this] class IntEvaluator(n: Int) extends Evaluator {
    override def evaluate(context: EvaluationContextImpl): IntegerValue =
      context.getDebugProcess.getVirtualMachineProxy.mirrorOf(n)
  }
}
