package org.jetbrains.plugins.scala.debugger.ui

import com.intellij.debugger.DebuggerContext
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContextImpl}
import com.intellij.debugger.jdi.LocalVariableProxyImpl
import com.intellij.debugger.ui.impl.watch.{ArrayElementDescriptorImpl, LocalVariableDescriptorImpl, ValueDescriptorImpl}
import com.intellij.debugger.ui.tree.render.{NodeRenderer, OnDemandRenderer}
import com.intellij.debugger.ui.tree.{DescriptorWithParentObject, NodeDescriptor}
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiExpression
import com.intellij.ui.LayeredIcon
import com.sun.jdi._
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.debugger.DebuggerBundle
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException

import java.util.Collections
import java.util.concurrent.CompletableFuture
import javax.swing.Icon
import scala.collection.mutable
import scala.jdk.CollectionConverters._

private object LazyValDescriptor {
  def create(project: Project, ref: ObjectReference, field: Field, evaluationContext: EvaluationContextImpl): NodeDescriptor = {
    val allFields = ref.referenceType().allFields().asScala
    val allMethods = ref.referenceType().allMethods().asScala
    val isScala3 = allFields.exists(_.name().startsWith(ScalaClassRenderer.Offset))
    if (isScala3) createLazyValDescriptorScala3(project, ref, field, allFields, allMethods, evaluationContext)
    else createLazyValDescriptorScala2(project, ref, field, allFields, allMethods)
  }

  private def createLazyValDescriptorScala2(
    project: Project,
    ref: ObjectReference,
    field: Field,
    allFields: mutable.Buffer[Field],
    allMethods: mutable.Buffer[Method]
  ): NodeDescriptor = {
    val lazyValFields = allFields.filter(ScalaClassRenderer.isLazyVal(ref, _))
    val index = lazyValFields.indexOf(field)
    val bitmapNumber = index / 64
    val bitmapExponent = index % 64
    val transient = if (field.isTransient) "trans$" else ""
    val bitmapName = s"bitmap$$$transient$bitmapNumber"
    val bitmapField = allFields.find(_.name() == bitmapName).getOrElse(throw EvaluationException(DebuggerBundle.message("could.not.find.bitmap.field", bitmapName)))
    val bitmapValue = ref.getValue(bitmapField).asInstanceOf[PrimitiveValue].longValue()
    val bitmapMask = 1L << bitmapExponent
    val isInitialized = (bitmapValue & bitmapMask) != 0
    val init = findInitializerScala2(field, allMethods)
    if (isInitialized) new FieldLazyValDescriptor(project, ref, init)
    else new NotInitializedFieldLazyValDescriptor(project, ref, init, showInitializeAction = true)
  }

  private def createLazyValDescriptorScala3(
    project: Project,
    ref: ObjectReference,
    field: Field,
    allFields: mutable.Buffer[Field],
    allMethods: mutable.Buffer[Method],
    evaluationContext: EvaluationContextImpl
  ): NodeDescriptor = {
    val lazyValFields = allFields.filter(ScalaClassRenderer.isLazyVal(ref, _))
    val index = lazyValFields.indexOf(field)
    val bitmapIndex = index / 32
    val bitmapExponent = index % 32
    val bitmapName = s"${bitmapIndex}bitmap$$"
    val bitmapField = allFields.find(_.name().contains(bitmapName))
    val init = findInitializerScala3(field, allMethods)

    bitmapField match {
      case Some(bitmap) =>
        // Old lazy val encoding, Scala 3.0, 3.1 and 3.2
        val bitmapValue = ref.getValue(bitmap).asInstanceOf[PrimitiveValue].longValue()
        val bitmapMask = (1L << bitmapExponent) + (1L << (bitmapExponent + 1))
        val isInitialized = (bitmapValue & bitmapMask) == bitmapMask
        if (isInitialized) new FieldLazyValDescriptor(project, ref, init)
        else new NotInitializedFieldLazyValDescriptor(project, ref, init, showInitializeAction = true)
      case None =>
        // New lazy val encoding, Scala 3.3+
        ref.getValue(field) match {
          case null =>
            val frameProxy = evaluationContext.getFrameProxy
            val isInitializable =
              if (frameProxy eq null)
                false
              else
                frameProxy.threadProxy().frames().asScala.forall { frame =>
                  val methodName = frame.location().method().name()
                  !methodName.contains("$lzyINIT")
                }

            new NotInitializedFieldLazyValDescriptor(project, ref, init, showInitializeAction = isInitializable)
          case obj: ObjectReference =>
            val name = obj.referenceType().name()
            name match {
              case "scala.runtime.LazyVals$Evaluating$" | "scala.runtime.LazyVals$Waiting" =>
                new IntermediateStateFieldLazyValDescriptor(project, ref, init, DebuggerBundle.message("lazy.val.being.initialized"))
              case _ =>
                new FieldLazyValDescriptor(project, ref, init)
            }
        }
    }
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

private class FieldLazyValDescriptor(project: Project, ref: ObjectReference, init: Method)
  extends ValueDescriptorImpl(project) with DescriptorWithParentObject {

  override def getName: String = init.name()

  override def getType: Type = init.returnType()

  override def getRenderer(process: DebugProcessImpl): CompletableFuture[NodeRenderer] =
    getRenderer(getType, process)

  override def calcValue(evaluationContext: EvaluationContextImpl): Value =
    evaluationContext.getDebugProcess.invokeMethod(evaluationContext, ref, init, Collections.emptyList())

  override def getDescriptorEvaluation(context: DebuggerContext): PsiExpression =
    throw new EvaluateException("Lazy val evaluation is not supported")

  override def getObject: ObjectReference = ref
}

private final class NotInitializedFieldLazyValDescriptor(project: Project, ref: ObjectReference, init: Method, showInitializeAction: Boolean)
  extends FieldLazyValDescriptor(project, ref, init) {

  OnDemandRenderer.ON_DEMAND_CALCULATED.set(this, false)
  setOnDemandPresentationProvider { node =>
    val typeName = init.returnTypeName()
    node.setPresentation(AllIcons.Debugger.Value, typeName, DebuggerBundle.message("lazy.val.not.initialized"), false)
    if (showInitializeAction) {
      node.setFullValueEvaluator(OnDemandRenderer.createFullValueEvaluator(DebuggerBundle.message("initialize.lazy.val")))
    }
  }
}

private final class IntermediateStateFieldLazyValDescriptor(project: Project, ref: ObjectReference, init: Method, @Nls presentation: String)
  extends FieldLazyValDescriptor(project, ref, init) {

  OnDemandRenderer.ON_DEMAND_CALCULATED.set(this, false)
  setOnDemandPresentationProvider { node =>
    val typeName = init.returnTypeName()
    node.setPresentation(AllIcons.Debugger.Value, typeName, presentation, false)
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
