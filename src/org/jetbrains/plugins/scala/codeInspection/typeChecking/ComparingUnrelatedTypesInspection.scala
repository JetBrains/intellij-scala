package org.jetbrains.plugins.scala
package codeInspection.typeChecking

import org.jetbrains.plugins.scala.codeInspection.{InspectionBundle, AbstractInspection}
import ComparingUnrelatedTypesInspection._
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.lang.psi.types._
import result.Success
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import extensions.toPsiClassExt

/**
 * Nikolay.Tropin
 * 5/30/13
 */

object ComparingUnrelatedTypesInspection {
  val inspectionName = InspectionBundle.message("comparing.unrelated.types.name")
  val inspectionId = "ComparingUnrelatedTypes"
}

class ComparingUnrelatedTypesInspection extends AbstractInspection(inspectionId, inspectionName){
  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case MethodRepr(expr, Some(left), Some(oper), Seq(right)) if Seq("==", "!=", "ne", "eq", "equals") contains oper.refName =>
      //getType() for the reference on the left side returns singleton type, little hack here
      val leftOnTheRight = ScalaPsiElementFactory.createExpressionWithContextFromText(left.getText, right.getParent, right)
      Seq(leftOnTheRight, right) map (_.getType()) match {
        case Seq(Success(leftType, _), Success(rightType, _)) if cannotBeCompared(leftType, rightType) =>
          holder.registerProblem(expr, inspectionName, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        case _ =>
      }
  }

  def cannotBeCompared(type1: ScType, type2: ScType): Boolean = {
    val Seq(class1, class2) = Seq(type1, type2).map(ScType.extractClass(_))
    def oneIsFinal = (class1 ++ class2).exists(_.isEffectivelyFinal)
    def notGeneric = !Seq(type1, type2).exists(_.isInstanceOf[ScTypeParameterType])
    def notParameterized = !Seq(type1, type2).exists(_.isInstanceOf[ScParameterizedType]) //todo better checking of ScParameterizedType
    def noConformance = {
      lazy val (unboxed1, unboxed2) = (StdType.unboxedType(type1), StdType.unboxedType(type2))
      !type1.conforms(type2) && !type2.conforms(type1) && !unboxed1.weakConforms(unboxed2) && !unboxed2.weakConforms(unboxed1)
    }
    oneIsFinal && notGeneric && notParameterized && noConformance
  }
}