package org.jetbrains.plugins.scala.debugger.ui

import java.util.concurrent.CompletableFuture

import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.ui.tree.render.ClassRenderer
import com.sun.jdi.ClassType
import com.sun.jdi.Field
import com.sun.jdi.ObjectReference
import com.sun.jdi.Type
import org.jetbrains.plugins.scala.debugger.ScalaSyntheticProvider.hasSpecialization
import org.jetbrains.plugins.scala.debugger.ScalaSyntheticProvider.isSpecialization

import scala.jdk.CollectionConverters._

/**
  * Nikolay.Tropin
  * 14-Mar-17
  */
class ClassWithSpecializedFieldsRenderer extends ClassRenderer {

  override def getUniqueId: String = "ClassWithSpecializedFieldsRenderer"

  def isApplicableFor(tpe: Type): CompletableFuture[java.lang.Boolean] = tpe match {
    case ct: ClassType =>
      CompletableFuture.completedFuture(ct.fields().asScala.exists(isSpecialization))
    case _ => CompletableFuture.completedFuture(false)
  }

  override def shouldDisplay(context: EvaluationContext, objInstance: ObjectReference, field: Field): Boolean = {
    super.shouldDisplay(context, objInstance, field) &&
      !hasSpecialization(field, Some(objInstance.referenceType()))
  }
}