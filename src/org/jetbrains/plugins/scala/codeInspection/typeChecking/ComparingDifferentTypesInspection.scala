package org.jetbrains.plugins.scala
package codeInspection.typeChecking

import org.jetbrains.plugins.scala.codeInspection.{AbstractInspection, InspectionBundle}
import ComparingDifferentTypesInspection._
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.lang.annotation.AnnotationSession

/**
 * Nikolay.Tropin
 * 5/30/13
 */

object ComparingDifferentTypesInspection {
  val inspectionName = "Comparing different types" //move to inspection bundle
  val inspectionId = "ComparingDifferentTypes"
}

class ComparingDifferentTypesInspection extends AbstractInspection(inspectionId, inspectionName){
  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case MethodRepr(expr, Some(left), Some(oper), Seq(right)) if Seq("==", "!=") contains oper.refName =>
      Seq(left, right) map (_.getType(TypingContext.empty)) match {
        case Seq(Success(leftType, _), Success(rightType, _))
          if !leftType.conforms(rightType) && !rightType.conforms(leftType) =>
          val annotationHolder = new AnnotationHolderImpl(new AnnotationSession(holder.getFile))

          //holder.registerProblem(expr, inspectionName, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        case _ =>
      }
  }

  def foo() {
    def bar(y: Int, z: Int) = 0
    Nil.foldLeft(0)(bar)
    def bar2(x: Int)(y: Int) = true
    List(false).filter(!_)
    val a: (Int, Int) => Any = bar
  }
}