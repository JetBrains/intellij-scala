package org.jetbrains.plugins.scala
package codeInspection.etaExpansion

import org.jetbrains.plugins.scala.codeInspection.{AbstractFix, InspectionBundle, AbstractInspection}
import ConvertibleToMethodValueInspection._
import com.intellij.codeInspection.{ProblemHighlightType, ProblemDescriptor, ProblemsHolder}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import scala.Some
import org.jetbrains.plugins.scala.lang.psi.types.result.Success
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * Nikolay.Tropin
 * 5/30/13
 */
object ConvertibleToMethodValueInspection {
  val inspectionName = InspectionBundle.message("convertible.to.method.value.name")
  val inspectionId = "ConvertibleToMethodValue"
}

class ConvertibleToMethodValueInspection extends AbstractInspection(inspectionId, inspectionName){
  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case MethodRepr(expr, _, Some(_), args) =>
      if (args.size > 0 && args.forall(arg => arg.isInstanceOf[ScUnderscoreSection] && ScUnderScoreSectionUtil.isUnderscore(arg)))
        registerProblem(holder, expr, InspectionBundle.message("convertible.to.method.value.anonymous.hint"))
    case und: ScUnderscoreSection if und.bindingExpr.isDefined =>
      registerProblem(holder, und, InspectionBundle.message("convertible.to.method.value.eta.hint"))
  }

  private def registerProblem(holder: ProblemsHolder, expr: ScExpression, hint: String) {
    possibleReplacements(expr).find(isSuitableForReplace(expr, _)).foreach { replacement =>
      holder.registerProblem(expr, inspectionName,
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        new ConvertibleToMethodValueQuickFix(expr, replacement, hint))
    }
  }

  private def methodWithoutArgumentsText(expr: ScExpression): Seq[String] = expr match {
    case call: ScMethodCall => Seq(call.getEffectiveInvokedExpr.getText)
    case ScInfixExpr(_, oper, right) if !ScalaNamesUtil.isOperatorName(oper.refName) =>
      val infixCopy = expr.copy.asInstanceOf[ScInfixExpr]
      infixCopy.getNode.removeChild(infixCopy.rOp.getNode)
      Seq(infixCopy.getText)
    case und: ScUnderscoreSection => und.bindingExpr.map(_.getText).toSeq
    case _ => Seq.empty
  }

  private def isSuitableForReplace(oldExpr: ScExpression, newExprText: String): Boolean = {
    val newExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(newExprText, oldExpr.getContext, oldExpr)
    oldExpr.expectedType(fromUnderscore = false) match {
      case Some(expectedType) if ScType.extractFunctionType(expectedType).isDefined =>
        def conformsExpected(expr: ScExpression): Boolean = expr.getType().getOrAny conforms expectedType
        conformsExpected(oldExpr) && conformsExpected(newExpr) && oldExpr.getType().getOrAny.conforms(newExpr.getType().getOrNothing)
      case None if newExprText endsWith "_" =>
        (oldExpr.getType(), newExpr.getType()) match {
          case (Success(oldType, _), Success(newType, _)) => oldType.equiv(newType)
          case _ => false
        }
      case _ => false
    }
  }

  private def possibleReplacements(expr: ScExpression): Seq[String] = {
    val withoutArguments = methodWithoutArgumentsText(expr)
    val withUnderscore =
      if (expr.getText endsWith "_") Nil
      else withoutArguments.map(_ + " _")

    withoutArguments ++ withUnderscore
  }
}

class ConvertibleToMethodValueQuickFix(expr: ScExpression, replacement: String, hint: String)
        extends AbstractFix(hint, expr){

  def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!expr.isValid) return
    val newExpr = ScalaPsiElementFactory.createExpressionFromText(replacement, expr.getManager)
    expr.replaceExpression(newExpr, removeParenthesis = true)
  }
}
