package org.jetbrains.plugins.scala
package codeInspection.typeChecking

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.{PsiMethod, PsiElement}
import com.siyeh.ig.psiutils.MethodUtils
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.codeInspection.typeChecking.ComparingUnrelatedTypesInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractInspection, InspectionBundle}
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, ResolvesTo}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.Success
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.annotation.tailrec

/**
 * Nikolay.Tropin
 * 5/30/13
 */

object ComparingUnrelatedTypesInspection {
  val inspectionName = InspectionBundle.message("comparing.unrelated.types.name")
  val inspectionId = "ComparingUnrelatedTypes"

  private val seqFunctions = Seq("contains", "indexOf", "lastIndexOf")

  def cannotBeCompared(type1: ScType, type2: ScType)
                      (implicit typeSystem: TypeSystem): Boolean = {
    if (undefinedTypeAlias(type1) || undefinedTypeAlias(type2)) return false

    val types = Seq(type1, type2).map(extractActualType)
    val Seq(unboxed1, unboxed2) =
      if (types.contains(Null)) types else types.map(StdType.unboxedType)

    if (isNumericType(unboxed1) && isNumericType(unboxed2)) return false

    ComparingUtil.isNeverSubType(unboxed1, unboxed2) && ComparingUtil.isNeverSubType(unboxed2, unboxed1)
  }

  def isNumericType(tp: ScType) = {
    tp match {
      case Byte | Char | Short | Int | Long | Float | Double => true
      case ScDesignatorType(c: ScClass) => c.supers.headOption.map(_.qualifiedName).contains("scala.math.ScalaNumber")
      case _ => false
    }
  }

  def undefinedTypeAlias(tp: ScType)
                        (implicit typeSystem: TypeSystem) = tp.isAliasType match {
    case Some(ScTypeUtil.AliasType(_, lower, upper)) =>
      lower.isEmpty || upper.isEmpty || !lower.get.equiv(upper.get)
    case _ => false
  }

  @tailrec
  def extractActualType(tp: ScType): ScType = {
    tp.isAliasType match {
      case Some(ScTypeUtil.AliasType(_, Success(rhs, _), _)) => extractActualType(rhs)
      case _ => tryExtractSingletonType(tp)
    }
  }

  private def tryExtractSingletonType(tp: ScType): ScType = ScType.extractDesignatorSingletonType(tp).getOrElse(tp)
}

class ComparingUnrelatedTypesInspection extends AbstractInspection(inspectionId, inspectionName){
  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case MethodRepr(expr, Some(left), Some(oper), Seq(right)) if Seq("==", "!=", "ne", "eq", "equals") contains oper.refName =>
      val needHighlighting = oper.resolve() match {
        case synth: ScSyntheticFunction => true
        case m: PsiMethod if MethodUtils.isEquals(m) => true
        case _ => false
      }
      implicit val typeSystem = holderTypeSystem(holder)
      if (needHighlighting) {
        //getType() for the reference on the left side returns singleton type, little hack here
        val leftOnTheRight = ScalaPsiElementFactory.createExpressionWithContextFromText(left.getText, right.getParent, right)
        Seq(leftOnTheRight, right) map (_.getType()) match {
          case Seq(Success(leftType, _), Success(rightType, _)) if cannotBeCompared(leftType, rightType) =>
            holder.registerProblem(expr, inspectionName, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          case _ =>
        }
      }
    case MethodRepr(_, Some(baseExpr), Some(ResolvesTo(fun: ScFunction)), Seq(arg, _*)) if mayNeedHighlighting(fun) =>
      implicit val typeSystem = holderTypeSystem(holder)
      for {
        ScParameterizedType(_, Seq(elemType)) <- baseExpr.getType().map(tryExtractSingletonType)
        argType <- arg.getType()
        if cannotBeCompared(elemType, argType)
      } {
        val (elemTypeText, argTypeText) = ScTypePresentation.different(elemType, argType)
        val message = InspectionBundle.message("comparing.unrelated.types.hint", elemTypeText, argTypeText)
        holder.registerProblem(arg, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    case IsInstanceOfCall(call)  =>
      implicit val typeSystem = holderTypeSystem(holder)
      val qualType = call.referencedExpr match {
        case ScReferenceExpression.withQualifier(q) => q.getType().toOption
        case _ => None
      }
      val argType = call.arguments.headOption.flatMap(_.getType().toOption)
      for {
        t1 <- qualType
        t2 <- argType
        if cannotBeCompared(t1, t2)
      } {
        holder.registerProblem(call, inspectionName, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
  }

  private def holderTypeSystem(holder: ProblemsHolder) = holder.getProject.typeSystem

  private def mayNeedHighlighting(fun: ScFunction): Boolean = {
    if (!seqFunctions.contains(fun.name)) return false
    val className = fun.containingClass.qualifiedName
    className.startsWith("scala.collection") && className.contains("Seq") && seqFunctions.contains(fun.name) ||
        Seq("scala.Option", "scala.Some").contains(className) && fun.name == "contains"
  }
}