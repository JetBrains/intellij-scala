package org.jetbrains.plugins.scala.debugger
package ui

import com.intellij.debugger.engine.evaluation.expression.{Evaluator, ExpressionEvaluator, ExpressionEvaluatorImpl, IdentityEvaluator}
import com.intellij.debugger.engine.evaluation.{EvaluationContext, EvaluationContextImpl}
import com.intellij.debugger.engine.{DebuggerUtils, JVMNameUtil}
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.tree.ValueDescriptor
import com.intellij.debugger.ui.tree.render.{ChildrenBuilder, DescriptorLabelListener}
import com.sun.jdi._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.{ScalaFieldEvaluator, ScalaMethodEvaluator, ScalaTypeEvaluator}
import org.jetbrains.plugins.scala.debugger.filters.ScalaDebuggerSettings

import scala.reflect.NameTransformer

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

  override def calcLabel(descriptor: ValueDescriptor, context: EvaluationContext, labelListener: DescriptorLabelListener): String =
    descriptor.getType match {
      case ct: ClassType if isCollection(ct) =>
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
      case ct: ClassType if isCollection(ct) =>
        val ref = value.asInstanceOf[ObjectReference]
        val array = evaluateToArray(ref, context)
        val renderer = NodeRendererSettings.getInstance().getArrayRenderer
        renderer.buildChildren(array, builder, context)
      case _ =>
        super.buildChildren(value, builder, context)
    }
}

private[debugger] object ScalaCollectionRenderer {
  def isCollection(ct: ClassType): Boolean =
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

  def evaluateNonEmpty(ref: ObjectReference, context: EvaluationContext): Boolean =
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

  def evaluateToArray(ref: ObjectReference, context: EvaluationContext): ObjectReference =
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

  private class IntEvaluator(n: Int) extends Evaluator {
    override def evaluate(context: EvaluationContextImpl): IntegerValue =
      context.getDebugProcess.getVirtualMachineProxy.mirrorOf(n)
  }
}
