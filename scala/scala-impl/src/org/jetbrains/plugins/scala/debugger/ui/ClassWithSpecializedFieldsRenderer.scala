package org.jetbrains.plugins.scala.debugger.ui

import java.util.concurrent.CompletableFuture

import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.impl.DebuggerUtilsAsync
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

  //noinspection UnstableApiUsage
  setIsApplicableChecker {
    case ct: ClassType =>
      DebuggerUtilsAsync.allFields(ct).thenApply(_.asScala.exists(isSpecialization))
    case _ => CompletableFuture.completedFuture(false)
  }

  //noinspection ScalaDeprecation
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