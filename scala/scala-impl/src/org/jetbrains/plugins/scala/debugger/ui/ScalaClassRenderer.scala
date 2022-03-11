package org.jetbrains.plugins.scala.debugger
package ui

import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.tree.ValueDescriptor
import com.intellij.debugger.ui.tree.render.{ClassRenderer, DescriptorLabelListener}
import com.sun.jdi._
import org.jetbrains.plugins.scala.ScalaBundle

class ScalaClassRenderer extends ClassRenderer {

  import ScalaClassRenderer._

  override val getUniqueId: String = getClass.getSimpleName

  override def getName: String = ScalaBundle.message("scala.class.renderer")

  def isApplicableFor(tpe: Type): Boolean = tpe match {
    case ct: ClassType => ct.sourceName().endsWith(".scala")
    case _ => false
  }

  override def shouldDisplay(context: EvaluationContext, objInstance: ObjectReference, field: Field): Boolean =
    !ScalaSyntheticProvider.hasSpecialization(field, Some(objInstance.referenceType())) &&
      !isModule(field) && !isBitmap(field) && !isOffset(field)

  override def calcLabel(descriptor: ValueDescriptor, context: EvaluationContext, labelListener: DescriptorLabelListener): String = {
    val renderer = NodeRendererSettings.getInstance().getToStringRenderer
    renderer.calcLabel(descriptor, context, labelListener)
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
