package org.jetbrains.plugins.scala.debugger.ui

import com.intellij.debugger.DebuggerContext
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.jdi.LocalVariableProxyImpl
import com.intellij.debugger.ui.impl.watch.{ArrayElementDescriptorImpl, FieldDescriptorImpl, LocalVariableDescriptorImpl}
import com.intellij.debugger.ui.tree.NodeDescriptor
import com.intellij.debugger.ui.tree.render.{NodeRenderer, OnDemandRenderer}
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiExpression
import com.intellij.ui.LayeredIcon
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.DebuggerBundle
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException

import java.util.Collections
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
    if (isInitialized) new FieldLazyValDescriptor(project, ref, field, init)
    else new NotInitializedFieldLazyValDescriptor(project, ref, field, init)
  }

  private def isInitializedScala2(ref: ObjectReference, field: Field, allFields: mutable.Buffer[Field]): Boolean = {
    val lazyValFields = allFields.filter(ScalaClassRenderer.isLazyVal(ref, _))
    val index = lazyValFields.indexOf(field)
    val bitmapNumber = index / 64
    val bitmapExponent = index % 64
    val transient = if (field.isTransient) "trans$" else ""
    val bitmapName = s"bitmap$$$transient$bitmapNumber"
    val bitmapField = allFields.find(_.name() == bitmapName).getOrElse(throw EvaluationException(DebuggerBundle.message("could.not.find.bitmap.field", bitmapName)))
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
    val bitmapField = allFields.find(_.name().contains(bitmapName)).getOrElse(throw EvaluationException(DebuggerBundle.message("could.not.find.bitmap.field", bitmapName)))
    val bitmapValue = ref.getValue(bitmapField).asInstanceOf[PrimitiveValue].longValue()
    val bitmapMask = (1L << bitmapExponent) + (1L << (bitmapExponent + 1))
    (bitmapValue & bitmapMask) == bitmapMask
  }

  private def findInitializerScala2(field: Field, allMethods: mutable.Buffer[Method]): Method = {
    val name = field.name()
    allMethods.find(_.name() == field.name()).getOrElse(throw EvaluationException(DebuggerBundle.message("could.not.find.accessor.method", name)))
  }

  private def findInitializerScala3(field: Field, allMethods: mutable.Buffer[Method]): Method = {
    val name = field.name().split("\\$lzy")(0)
    allMethods.find(_.name() == name).getOrElse(throw EvaluationException(DebuggerBundle.message("could.not.find.accessor.method", name)))
  }
}

private class FieldLazyValDescriptor(project: Project, ref: ObjectReference, field: Field, init: Method)
  extends FieldDescriptorImpl(project, ref, field) {

  override def getName: String = init.name()

  override def getType: Type = field.`type`()

  override def getRenderer(process: DebugProcessImpl): CompletableFuture[NodeRenderer] =
    getRenderer(getType, process)
}

private final class NotInitializedFieldLazyValDescriptor(project: Project, ref: ObjectReference, field: Field, init: Method)
  extends FieldLazyValDescriptor(project, ref, field, init) {

  OnDemandRenderer.ON_DEMAND_CALCULATED.set(this, false)
  setOnDemandPresentationProvider { node =>
    node.setFullValueEvaluator(OnDemandRenderer.createFullValueEvaluator(DebuggerBundle.message("initialize.lazy.val")))
    val typeName = field.typeName()
    node.setPresentation(icon, typeName, DebuggerBundle.message("lazy.val.not.initialized"), false)
  }

  override def calcValue(context: EvaluationContextImpl): Value =
    context.getDebugProcess.invokeMethod(context, ref, init, Seq.empty.asJava)

  private val icon: Icon = {
    var base = AllIcons.Nodes.Field
    if (field.isFinal) base = LayeredIcon.layeredIcon(Array(base, AllIcons.Nodes.FinalMark))
    if (field.isStatic) base = LayeredIcon.layeredIcon(Array(base, AllIcons.Nodes.StaticMark))
    base
  }
}

private[debugger] class LocalLazyValDescriptor(project: Project, proxy: LocalVariableProxyImpl, lazyRefType: Type, init: Method)
  extends LocalVariableDescriptorImpl(project, proxy) {

  override def getName: String = {
    val name = super.getName
    if (name.endsWith("$lzy")) name.dropRight(4) else name
  }

  override def getType: Type = lazyRefType

  override def isPrimitive: Boolean = getType.name() != "scala.runtime.LazyRef"

  override def calcValue(context: EvaluationContextImpl): Value = {
    val lazyRefValue = proxy.getFrame.getValue(proxy)
    val thisObject = context.computeThisObject().asInstanceOf[ObjectReference]
    val refType = init.declaringType()
    val args = Collections.singletonList(lazyRefValue)
    refType match {
      case ct: ClassType if init.isStatic => context.getDebugProcess.invokeMethod(context, ct, init, args)
      case it: InterfaceType if init.isStatic => context.getDebugProcess.invokeMethod(context, it, init, args)
      case _ => context.getDebugProcess.invokeMethod(context, thisObject, init, args)
    }
  }
}

private[debugger] final class NotInitializedLocalLazyValDescriptor(project: Project, proxy: LocalVariableProxyImpl, lazyRefType: Type, init: Method)
  extends LocalLazyValDescriptor(project, proxy, lazyRefType, init) {

  OnDemandRenderer.ON_DEMAND_CALCULATED.set(this, false)
  setOnDemandPresentationProvider { node =>
    node.setFullValueEvaluator(OnDemandRenderer.createFullValueEvaluator(DebuggerBundle.message("initialize.lazy.val")))
    node.setPresentation(icon, typeString, DebuggerBundle.message("lazy.val.not.initialized"), false)
  }

  private val icon: Icon = LayeredIcon.layeredIcon(Array(AllIcons.Nodes.Variable, AllIcons.Nodes.FinalMark))

  private val typeString: String = getType.name() match {
    case "scala.runtime.LazyRef" => "lazy AnyRef reference"
    case "scala.runtime.LazyBoolean" => "lazy Boolean"
    case "scala.runtime.LazyByte" => "lazy Byte"
    case "scala.runtime.LazyChar" => "lazy Char"
    case "scala.runtime.LazyShort" => "lazy Short"
    case "scala.runtime.LazyInt" => "lazy Int"
    case "scala.runtime.LazyLong" => "lazy Long"
    case "scala.runtime.LazyFloat" => "lazy Float"
    case "scala.runtime.LazyDouble" => "lazy Double"
    case "scala.runtime.LazyUnit" => "lazy Unit"
  }
}

private final class CollectionElementDescriptor(project: Project, index: Int, value: Value)
  extends ArrayElementDescriptorImpl(project, null, index) {

  setValue(value)

  override def getDescriptorEvaluation(context: DebuggerContext): PsiExpression =
    throw EvaluationException(DebuggerBundle.message("collection.element.descriptors.evaluation.not.supported"))
}
