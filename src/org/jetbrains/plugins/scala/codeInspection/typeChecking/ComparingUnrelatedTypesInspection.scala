package org.jetbrains.plugins.scala
package codeInspection.typeChecking

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.codeInspection.typeChecking.ComparingUnrelatedTypesInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractInspection, InspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.Success

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
    case MethodRepr(_, Some(baseExpr), Some(ref), Seq(arg)) if ref.refName == "contains" =>
      ref.resolve() match {
        case fun: ScFunction if fun.containingClass.qualifiedName == "scala.collection.SeqLike" =>
          for {
            ScParameterizedType(_, Seq(elemType)) <- baseExpr.getType()
            argType <- arg.getType()
            if cannotBeCompared(elemType, argType)
          } {
            val message = s"$inspectionName: ${elemType.presentableText} and ${argType.presentableText}"
            holder.registerProblem(arg, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          }
        case _ =>
      }
  }

  def cannotBeCompared(type1: ScType, type2: ScType): Boolean = {
    val types = Seq(type1, type2)
    val Seq(unboxed1, unboxed2) =
      if (types.contains(Null)) types else types.map(StdType.unboxedType)
    ComparingUtil.isNeverSubType(unboxed1, unboxed2) && ComparingUtil.isNeverSubType(unboxed2, unboxed1)
  }
}