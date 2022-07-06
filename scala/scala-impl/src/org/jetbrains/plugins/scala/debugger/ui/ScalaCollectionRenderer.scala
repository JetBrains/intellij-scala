package org.jetbrains.plugins.scala.debugger
package ui

import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.engine.evaluation.expression._
import com.intellij.debugger.engine.{DebuggerUtils, JVMNameUtil}
import com.intellij.debugger.ui.tree.render.{ChildrenBuilder, DescriptorLabelListener}
import com.intellij.debugger.ui.tree.{NodeDescriptor, ValueDescriptor}
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants
import com.sun.jdi._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator._
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

    onDebuggerManagerThread(context) {
      val hasDefiniteSize = evaluateHasDefiniteSize(ref, context)
      val size = if (hasDefiniteSize) evaluateSize(ref, context).toString else "?"
      s"size = $size"
    }.attempt.flatTap {
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

    for {
      strict <- onDebuggerManagerThread(context)(evaluateHasDefiniteSize(ref, context))
      _ <- if (strict) renderStrictCollection(ref, builder, context, 0) else renderNonStrictCollection(ref, builder, context, 0)
    } yield ()
  }

  private def renderStrictCollection(ref: ObjectReference, builder: ChildrenBuilder, context: EvaluationContext, index: Int): CompletableFuture[Unit] =
    onDebuggerManagerThread(context) {
      val dropped = evaluateDrop(ref, index, context)
      val taken = evaluateTake(dropped, 100, context)
      val vector = evaluateToVector(taken, context)
      val vectorSize = evaluateSize(vector, context)
      val descs = (0 until vectorSize).map { i =>
        val v = evaluateVectorApply(vector, i, context)
        val desc = new CollectionElementDescriptor(context.getProject, i + index, v)
        builder.getNodeManager.createNode(desc, context)
      }
      builder.addChildren(descs.asJava, false)
      val size = evaluateSize(ref, context)
      val remaining = math.max(size - vectorSize - index, 0)
      if (remaining == 0)
        builder.addChildren(List.empty.asJava, true)
      else {
        val runnable: Runnable = () => renderStrictCollection(ref, builder, context, index + 100)
        builder.tooManyChildren(remaining, runnable)
      }
    }

  private def renderNonStrictCollection(ref: ObjectReference, builder: ChildrenBuilder, context: EvaluationContext, index: Int): CompletableFuture[Unit] =
    onDebuggerManagerThread(context) {
      def renderNextElement(index: Int): CompletableFuture[Unit] = onDebuggerManagerThread(context) {
        val dropped = evaluateDrop(ref, index, context)
        val taken = evaluateTake(dropped, 1, context)
        val vector = evaluateToVector(taken, context)
        val length = evaluateSize(vector, context)
        if (length == 1) {
          val desc = new CollectionElementDescriptor(context.getProject, index, evaluateVectorApply(vector, 0, context))
          val node = builder.getNodeManager.createNode(desc, context)
          builder.addChildren(List(node).asJava, false)
        }
      }

      val newIndex = index + 10
      for {
        _ <- (index until newIndex)
          .map(n => () => renderNextElement(n))
          .foldLeft(CompletableFuture.completedFuture(()))((acc, fn) => acc.flatMap(_ => fn()))
        _ <- onDebuggerManagerThread(context) {
          val hasDefiniteSize = evaluateHasDefiniteSize(ref, context)
          val size = if (hasDefiniteSize) Some(evaluateSize(ref, context)) else None
          if (size.isEmpty) {
            val runnable: Runnable = () => renderNonStrictCollection(ref, builder, context, newIndex)
            builder.tooManyChildren(-1, runnable)
          } else {
            builder.addChildren(List.empty.asJava, true)
          }
        }
      } yield ()
    }
}

private object ScalaCollectionRenderer {
  private def isCollection(ct: ClassType): Boolean = {
    def isView: Boolean =
      DebuggerUtils.instanceOf(ct, "scala.collection.View") ||
        DebuggerUtils.instanceOf(ct, "scala.collection.IterableView")

    DebuggerUtils.instanceOf(ct, "scala.collection.Iterable") && !isView
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
      null, // Different collection types have different `take` method signatures, depending on specializations, search only by name
      Seq(new IntEvaluator(n))
    ).asExpressionEvaluator.evaluate(context).asInstanceOf[ObjectReference]

  def evaluateDrop(ref: ObjectReference, n: Int, context: EvaluationContext): ObjectReference =
    evaluateIterableOperation("drop")(ref, n, context)

  def evaluateTake(ref: ObjectReference, n: Int, context: EvaluationContext): ObjectReference =
    evaluateIterableOperation("take")(ref, n, context)

  def evaluateToVector(ref: ObjectReference, context: EvaluationContext): ObjectReference =
    ScalaMethodEvaluator(
      new IdentityEvaluator(ref),
      "toVector",
      JVMNameUtil.getJVMRawText("()Lscala/collection/immutable/Vector;"),
      Seq.empty
    ).asExpressionEvaluator.evaluate(context).asInstanceOf[ObjectReference]

  def evaluateVectorApply(ref: ObjectReference, n: Int, context: EvaluationContext): Value =
    ScalaMethodEvaluator(
      new IdentityEvaluator(ref),
      "apply",
      JVMNameUtil.getJVMRawText("(I)Ljava/lang/Object;"),
      Seq(new IntEvaluator(n))
    ).asExpressionEvaluator.evaluate(context)

  private[this] implicit class EvaluatorToExpressionEvaluatorOps(private val evaluator: Evaluator) extends AnyVal {
    def asExpressionEvaluator: ExpressionEvaluator = new ExpressionEvaluatorImpl(evaluator)
  }
}
