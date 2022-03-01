package org.jetbrains.plugins.scala.debugger.ui

import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.tree.ValueDescriptor
import com.intellij.debugger.ui.tree.render.{ClassRenderer, DescriptorLabelListener}
import com.sun.jdi.{ClassType, Field, ObjectReference, Type}

import java.util.concurrent.CompletableFuture

class ScalaClassRenderer extends ClassRenderer {

  import ScalaClassRenderer._

  override val getUniqueId: String = getClass.getSimpleName

  def isApplicableFor(tpe: Type): CompletableFuture[java.lang.Boolean] = tpe match {
    case ct: ClassType =>
      val isScala = ct.sourceName().endsWith(".scala")
      val isCollection = !ct.name().startsWith("scala.collection.")
      CompletableFuture.completedFuture(isScala && isCollection)
    case _ => CompletableFuture.completedFuture(false)
  }

  override def shouldDisplay(context: EvaluationContext, objInstance: ObjectReference, field: Field): Boolean =
    !isModule(field) && !isBitmap(field) && !isOffset(field)

  override def calcLabel(descriptor: ValueDescriptor, evaluationContext: EvaluationContext, labelListener: DescriptorLabelListener): String = {
    val renderer = NodeRendererSettings.getInstance().getToStringRenderer
    renderer.calcLabel(descriptor, evaluationContext, labelListener)
  }
}

private object ScalaClassRenderer {
  private val Module: String = "MODULE$"

  private val Bitmap: String = "bitmap$"

  private val Offset: String = "OFFSET$"

  def isModule(f: Field): Boolean = f.name() == Module

  def isBitmap(f: Field): Boolean = f.name().contains(Bitmap)

  def isOffset(f: Field): Boolean = f.name().startsWith(Offset)
}
