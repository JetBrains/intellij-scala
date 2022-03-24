package org.jetbrains.plugins.scala.debugger.ui

import com.intellij.debugger.DebuggerContext
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.ui.impl.watch.{ArrayElementDescriptorImpl, FieldDescriptorImpl}
import com.intellij.debugger.ui.tree.NodeDescriptor
import com.intellij.debugger.ui.tree.render.{ChildrenBuilder, NodeRenderer, OnDemandRenderer}
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiExpression
import com.intellij.ui.LayeredIcon
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation
import com.sun.jdi._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.debugger.ui.util._

import java.util.concurrent.CompletableFuture
import javax.swing.Icon
import scala.collection.mutable
import scala.jdk.CollectionConverters._

private object LazyValDescriptor {
  def create(project: Project, ref: ObjectReference, field: Field): NodeDescriptor = {
    val allFields = ref.referenceType().allFields().asScala
    val allMethods = ref.referenceType().allMethods().asScala
    val isScala3 = allFields.exists(_.name().startsWith(ScalaClassRenderer.Offset))
    val isInitializedFunction = if (isScala3) isInitializedScala3 _ else isInitializedScala2 _
    val findInit = if (isScala3) findInitializerScala3 _ else findInitializerScala2 _
    val isInitialized = isInitializedFunction(ref, field, allFields)
    val init = findInit(field, allMethods)
    if (isInitialized) new LazyValDescriptor(project, ref, field, init)
    else new NotInitializedLazyValDescriptor(project, ref, field, init)
  }

  private def isInitializedScala2(ref: ObjectReference, field: Field, allFields: mutable.Buffer[Field]): Boolean = {
    val lazyValFields = allFields.filter(ScalaClassRenderer.isLazyVal(ref, _))
    val index = lazyValFields.indexOf(field)
    val bitmapNumber = index / 64
    val bitmapExponent = index % 64
    val bitmapName = s"bitmap$$$bitmapNumber"
    val bitmapField = allFields.find(_.name() == bitmapName).getOrElse(throw EvaluationException(ScalaBundle.message("could.not.find.bitmap.field", bitmapName)))
    val bitmapValue = ref.getValue(bitmapField).asInstanceOf[PrimitiveValue].longValue()
    val bitmapMask = 1L << bitmapExponent
    (bitmapValue & bitmapMask) != 0
  }

  private def isInitializedScala3(ref: ObjectReference, field: Field, allFields: mutable.Buffer[Field]): Boolean = {
    val lazyValFields = allFields.filter(ScalaClassRenderer.isLazyVal(ref, _))
    val index = lazyValFields.indexOf(field)
    val bitmapIndex = index / 32
    val bitmapExponent = index % 32
    val bitmapName = s"${bitmapIndex}bitmap$$"
    val bitmapField = allFields.find(_.name().contains(bitmapName)).getOrElse(throw EvaluationException(ScalaBundle.message("could.not.find.bitmap.field", bitmapName)))
    val bitmapValue = ref.getValue(bitmapField).asInstanceOf[PrimitiveValue].longValue()
    val bitmapMask = (1L << bitmapExponent) + (1L << (bitmapExponent + 1))
    (bitmapValue & bitmapMask) == bitmapMask
  }

  private def findInitializerScala2(field: Field, allMethods: mutable.Buffer[Method]): Method = {
    val name = field.name()
    allMethods.find(_.name() == field.name()).getOrElse(throw EvaluationException(ScalaBundle.message("could.not.find.accessor.method", name)))
  }

  private def findInitializerScala3(field: Field, allMethods: mutable.Buffer[Method]): Method = {
    val name = field.name().split("\\$lzy")(0)
    allMethods.find(_.name() == name).getOrElse(throw EvaluationException(ScalaBundle.message("could.not.find.accessor.method", name)))
  }
}

private class LazyValDescriptor(project: Project, ref: ObjectReference, field: Field, init: Method)
  extends FieldDescriptorImpl(project, ref, field) {

  override def getName: String = init.name()

  override def getType: Type = field.`type`()

  override def getRenderer(process: DebugProcessImpl): CompletableFuture[NodeRenderer] =
    getRenderer(getType, process)
}

private final class NotInitializedLazyValDescriptor(project: Project, ref: ObjectReference, field: Field, init: Method)
  extends LazyValDescriptor(project, ref, field, init) {

  OnDemandRenderer.ON_DEMAND_CALCULATED.set(this, false)
  setOnDemandPresentationProvider { node =>
    node.setFullValueEvaluator(OnDemandRenderer.createFullValueEvaluator(ScalaBundle.message("initialize.lazy.val")))
    val typeName = getType.name()
    node.setPresentation(icon, typeName, ScalaBundle.message("lazy.val.not.initialized"), false)
  }

  override def calcValue(context: EvaluationContextImpl): Value =
    context.getDebugProcess.invokeMethod(context, ref, init, Seq.empty.asJava)

  private val icon: Icon = {
    var base = AllIcons.Nodes.Field
    if (field.isFinal) base = new LayeredIcon(base, AllIcons.Nodes.FinalMark)
    if (field.isStatic) base = new LayeredIcon(base, AllIcons.Nodes.StaticMark)
    base
  }
}

private final class CollectionElementDescriptor(project: Project, index: Int, value: Value)
  extends ArrayElementDescriptorImpl(project, null, index) {

  setValue(value)

  override def getDescriptorEvaluation(context: DebuggerContext): PsiExpression =
    throw EvaluationException("Evaluation of collection element descriptors is not supported")
}

private final class ExpandCollectionDescriptor(project: Project, index: Int, ref: ObjectReference, remaining: Option[Int], builder: ChildrenBuilder)
  extends ArrayElementDescriptorImpl(project, null, index) {

  import ScalaCollectionRenderer._

  OnDemandRenderer.ON_DEMAND_CALCULATED.set(this, false)
  setOnDemandPresentationProvider { node =>
    node.setFullValueEvaluator(OnDemandRenderer.createFullValueEvaluator("Expand"))
    val message = remaining.map(n => s"($n more items)").getOrElse("(More items)")
    node.setPresentation(AllIcons.Debugger.Value, new XRegularValuePresentation(message, null, " ... "), false)
  }

  override def calcValue(context: EvaluationContextImpl): Value = {
    val dropped = evaluateDrop(ref, index, context)
    val taken = evaluateTake(dropped, 100, context)
    val array = evaluateToArray(taken, context)
    val project = context.getProject
    val nodeManager = builder.getNodeManager

    for {
      _ <- onDebuggerManagerThread(context) {
        val elements = allValues(array)
        val descs = elements.drop(1).zipWithIndex.map { case (v, i) =>
          val desc = new CollectionElementDescriptor(project, i + 1 + index, v)
          nodeManager.createNode(desc, context)
        }
        builder.addChildren(descs.asJava, false)
      }
      hasDefiniteSize <- onDebuggerManagerThread(context)(evaluateHasDefiniteSize(ref, context))
      size <- if (hasDefiniteSize) onDebuggerManagerThread(context)(evaluateSize(ref, context)).map(Some(_)) else CompletableFuture.completedFuture(None)
      _ <- onDebuggerManagerThread(context) {
        val remaining = size.map(n => math.max(n - array.length() - index, 0))
        remaining match {
          case Some(0) => builder.addChildren(List.empty.asJava, true)
          case _ =>
            val desc = new ExpandCollectionDescriptor(project, index + 100, ref, remaining, builder)
            val node = nodeManager.createNode(desc, context)
            builder.addChildren(List(node).asJava, true)
        }
      }
    } yield ()

    array.getValue(0)
  }
}
