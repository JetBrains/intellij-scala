package org.jetbrains.plugins.scala.debugger
package ui

import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, ExpressionEvaluator, ExpressionEvaluatorImpl, IdentityEvaluator}
import com.intellij.debugger.engine.{DebuggerUtils, JVMNameUtil}
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.tree.render.{ChildrenBuilder, ClassRenderer, DescriptorLabelListener}
import com.intellij.debugger.ui.tree.{NodeDescriptor, ValueDescriptor}
import com.sun.jdi._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.{ScalaFieldEvaluator, ScalaMethodEvaluator, ScalaTypeEvaluator}

import java.util.concurrent.CompletableFuture
import scala.reflect.NameTransformer

class ScalaClassRenderer extends ClassRenderer {

  import ScalaClassRenderer._

  override val getUniqueId: String = getClass.getSimpleName

  override def getName: String = ScalaBundle.message("scala.class.renderer")

  def isApplicableFor(tpe: Type): CompletableFuture[java.lang.Boolean] = tpe match {
    case ct: ClassType =>
      val isScala = ct.sourceName().endsWith(".scala")
      CompletableFuture.completedFuture(isScala)
    case _ => CompletableFuture.completedFuture(false)
  }

  override def shouldDisplay(context: EvaluationContext, objInstance: ObjectReference, field: Field): Boolean =
    !ScalaSyntheticProvider.hasSpecialization(field, Some(objInstance.referenceType())) &&
      !isModule(field) && !isBitmap(field) && !isOffset(field)

  override def calcLabel(descriptor: ValueDescriptor, context: EvaluationContext, labelListener: DescriptorLabelListener): String =
    descriptor.getType match {
      case ct: ClassType if isFiniteCollection(ct) =>
        val ref = descriptor.getValue.asInstanceOf[ObjectReference]
        val hasDefiniteSize = evaluateHasDefiniteSize(ref, context)
        val size = if (hasDefiniteSize) evaluateSize(ref, context) else "?"
        val typeName = extractNonQualifiedName(ct.name())
        s"$typeName size = $size"

      case _ =>
        val renderer = NodeRendererSettings.getInstance().getToStringRenderer
        renderer.calcLabel(descriptor, context, labelListener)
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

private[debugger] object ScalaClassRenderer {
  private val Module: String = "MODULE$"

  private val Bitmap: String = "bitmap$"

  private val Offset: String = "OFFSET$"

  def isModule(f: Field): Boolean = f.name() == Module

  def isBitmap(f: Field): Boolean = f.name().contains(Bitmap)

  def isOffset(f: Field): Boolean = f.name().startsWith(Offset)

  implicit class EvaluatorToExpressionEvaluatorOps(private val evaluator: Evaluator) extends AnyVal {
    def asExpressionEvaluator: ExpressionEvaluator = new ExpressionEvaluatorImpl(evaluator)
  }

  private def isFiniteCollection(ct: ClassType): Boolean =
    DebuggerUtils.instanceOf(ct, "scala.collection.Iterable") && !isView(ct) && !isLazyList(ct)

  private def isView(ct: ClassType): Boolean =
    DebuggerUtils.instanceOf(ct, "scala.collection.View") ||
      DebuggerUtils.instanceOf(ct, "scala.collection.IterableView")

  private def isLazyList(ct: ClassType): Boolean =
    DebuggerUtils.instanceOf(ct, "scala.collection.immutable.LazyList") ||
      DebuggerUtils.instanceOf(ct, "scala.collection.immutable.Stream")

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
}
