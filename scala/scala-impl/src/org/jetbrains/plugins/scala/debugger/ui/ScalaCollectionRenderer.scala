package org.jetbrains.plugins.scala.debugger
package ui

import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, ExpressionEvaluator, ExpressionEvaluatorImpl, IdentityEvaluator}
import com.intellij.debugger.engine.{DebuggerUtils, JVMNameUtil}
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.tree.{NodeDescriptor, ValueDescriptor}
import com.intellij.debugger.ui.tree.render.{ChildrenBuilder, DescriptorLabelListener}
import com.sun.jdi._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.{ScalaFieldEvaluator, ScalaMethodEvaluator, ScalaTypeEvaluator}
import org.jetbrains.plugins.scala.debugger.filters.ScalaDebuggerSettings

import java.util.concurrent.CompletableFuture
import scala.reflect.NameTransformer

class ScalaCollectionRenderer extends ScalaClassRenderer {

  import ScalaCollectionRenderer._

  override def getName: String = ScalaBundle.message("scala.collection.renderer")

  override def isApplicableFor(tpe: Type): Boolean = {
    super.isApplicableFor(tpe) && {
      def shouldAlsoHandleLazyCollections: Boolean = !ScalaDebuggerSettings.getInstance().DO_NOT_DISPLAY_STREAMS

      tpe match {
        case ct: ClassType if isCollection(ct) && shouldAlsoHandleLazyCollections => true
        case ct: ClassType if isFiniteCollection(ct) => true
        case _ => false
      }
    }
  }

  override def calcLabel(descriptor: ValueDescriptor, context: EvaluationContext, labelListener: DescriptorLabelListener): String =
    descriptor.getType match {
      case ct: ClassType if isFiniteCollection(ct) =>
        val ref = descriptor.getValue.asInstanceOf[ObjectReference]
        val hasDefiniteSize = evaluateHasDefiniteSize(ref, context)
        val size = if (hasDefiniteSize) evaluateSize(ref, context) else "?"
        val typeName = extractNonQualifiedName(ct.name())
        s"$typeName size = $size"

      case _ =>
        super.calcLabel(descriptor, context, labelListener)
    }

  override def buildChildren(value: Value, builder: ChildrenBuilder, context: EvaluationContext): Unit =
    value.`type`() match {
      case ct: ClassType if isFiniteCollection(ct) =>
        val ref = value.asInstanceOf[ObjectReference]
        val array = evaluateToArray(ref, context)
        val renderer = NodeRendererSettings.getInstance().getArrayRenderer
        renderer.buildChildren(array, builder, context)
      case _ =>
        super.buildChildren(value, builder, context)
    }

  override def isExpandableAsync(value: Value,
                                 context: EvaluationContext,
                                 descriptor: NodeDescriptor): CompletableFuture[java.lang.Boolean] =
    value.`type`() match {
      case ct: ClassType if isFiniteCollection(ct) =>
        val ref = value.asInstanceOf[ObjectReference]
        val result = evaluateNonEmpty(ref, context) && evaluateHasDefiniteSize(ref, context)
        CompletableFuture.completedFuture(result)

      case _ =>
        super.isExpandableAsync(value, context, descriptor)
    }
}

private[debugger] object ScalaCollectionRenderer {
  def isCollection(ct: ClassType): Boolean =
    DebuggerUtils.instanceOf(ct, "scala.collection.Iterable")

  def isFiniteCollection(ct: ClassType): Boolean = {
    def isView: Boolean =
      DebuggerUtils.instanceOf(ct, "scala.collection.View") ||
        DebuggerUtils.instanceOf(ct, "scala.collection.IterableView")

    def isLazyList: Boolean =
      DebuggerUtils.instanceOf(ct, "scala.collection.immutable.LazyList") ||
        DebuggerUtils.instanceOf(ct, "scala.collection.immutable.Stream")

    isCollection(ct) && !isView && !isLazyList
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

  def evaluateNonEmpty(ref: ObjectReference, context: EvaluationContext): Boolean =
    ScalaMethodEvaluator(
      new IdentityEvaluator(ref),
      "nonEmpty",
      JVMNameUtil.getJVMRawText("()Z"),
      Seq.empty
    ).asExpressionEvaluator.evaluate(context).asInstanceOf[BooleanValue].value()

  private def evaluateToArray(ref: ObjectReference, context: EvaluationContext): ObjectReference =
    ScalaMethodEvaluator(
      new IdentityEvaluator(ref),
      "toArray",
      JVMNameUtil.getJVMRawText("(scala.reflect.ClassTag;)[Ljava/lang/Object"),
      Seq(ObjectClassTagEvaluator)
    ).asExpressionEvaluator.evaluate(context).asInstanceOf[ObjectReference]

  private val ObjectClassTagEvaluator: ScalaMethodEvaluator = {
    val classTagName = JVMNameUtil.getJVMRawText("scala.reflect.ClassTag$")
    val module = ScalaFieldEvaluator(new ScalaTypeEvaluator(classTagName), "MODULE$")
    ScalaMethodEvaluator(module, "Object", JVMNameUtil.getJVMRawText("()Lscala/reflect/ClassTag"), Seq.empty)
  }

  def extractNonQualifiedName(qualifiedName: String): String = {
    val decoded = NameTransformer.decode(qualifiedName)
    val index =
      if (decoded endsWith "`") decoded.substring(0, decoded.length - 1).lastIndexOf('`')
      else decoded.lastIndexOf('.')
    decoded.substring(index + 1)
  }

  implicit class EvaluatorToExpressionEvaluatorOps(private val evaluator: Evaluator) extends AnyVal {
    def asExpressionEvaluator: ExpressionEvaluator = new ExpressionEvaluatorImpl(evaluator)
  }
}
