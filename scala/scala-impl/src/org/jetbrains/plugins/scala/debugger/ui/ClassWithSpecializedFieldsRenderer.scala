package org.jetbrains.plugins.scala.debugger.ui

import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.ui.tree.render.ClassRenderer
import com.sun.jdi.{ClassType, Field, ObjectReference, Type}
import org.jetbrains.plugins.scala.debugger.ScalaSyntheticProvider.{isSpecialization, hasSpecialization}
import scala.collection.JavaConverters._

/**
  * Nikolay.Tropin
  * 14-Mar-17
  */
class ClassWithSpecializedFieldsRenderer extends ClassRenderer {

  override def getUniqueId: String = "ClassWithSpecializedFieldsRenderer"

  override def isApplicable(`type`: Type): Boolean = `type` match {
    case ct: ClassType =>
      ct.fields().asScala.exists(isSpecialization)
    case _ => false
  }

  override def shouldDisplay(context: EvaluationContext, objInstance: ObjectReference, field: Field): Boolean = {
    super.shouldDisplay(context, objInstance, field) &&
      !hasSpecialization(field, Some(objInstance.referenceType()))
  }
}
