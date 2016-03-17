package org.jetbrains.plugins.scala
package codeInspection.etaExpansion

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.codeInspection.etaExpansion.ConvertibleToMethodValueInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection, InspectionBundle}
import org.jetbrains.plugins.scala.extensions.{Both, PsiModifierListOwnerExt, ResolvesTo}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.Success
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
    case MethodRepr(expr, _, Some(ref), _)
      if ref.bind().exists(srr => srr.implicitType.nonEmpty || srr.implicitFunction.nonEmpty) =>
      //do nothing if implicit conversions are involved
    case MethodRepr(expr, Some(qual), Some(_), args) =>
      if (allArgsUnderscores(args) && onlyStableValuesUsed(qual))
        registerProblem(holder, expr, InspectionBundle.message("convertible.to.method.value.anonymous.hint"))
    case und: ScUnderscoreSection if und.bindingExpr.isDefined =>
      val isInParameterOfParameterizedClass = PsiTreeUtil.getParentOfType(und, classOf[ScClassParameter]) match {
        case null => false
        case cp => cp.containingClass.hasTypeParameters
      }
      def checkStable() = und.bindingExpr.get match {
        case ScReferenceExpression.withQualifier(qual) => onlyStableValuesUsed(qual)
        case e => onlyStableValuesUsed(e)
      }
      if (!isInParameterOfParameterizedClass && checkStable())
        registerProblem(holder, und, InspectionBundle.message("convertible.to.method.value.eta.hint"))
  }

  private def allArgsUnderscores(args: Seq[ScExpression]): Boolean = {
    args.nonEmpty && args.forall(arg => arg.isInstanceOf[ScUnderscoreSection] && ScUnderScoreSectionUtil.isUnderscore(arg))
  }

  private def onlyStableValuesUsed(qual: ScExpression): Boolean = {
    def isStable(named: PsiNamedElement) = ScalaPsiUtil.nameContext(named) match {
      case cp: ScClassParameter => !cp.isVar
      case f: PsiField => f.hasFinalModifier
      case o: ScObject => o.isLocal || ScalaPsiUtil.hasStablePath(o)
      case _: PsiMethod | _: ScVariable => false
      case _ => true
    }

    qual.depthFirst(e => !e.isInstanceOf[ScImportStmt]).forall {
      case _: ScNewTemplateDefinition => false
      case Both(_: ScReferenceExpression | ScConstructor.byReference(_), ResolvesTo(named: PsiNamedElement)) => isStable(named)
      case _ => true
    }
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

  private def isSuitableForReplace(oldExpr: ScExpression, newExprText: String)
                                  (implicit typeSystem: TypeSystem = oldExpr.typeSystem): Boolean = {
    val newExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(newExprText, oldExpr.getContext, oldExpr)
    oldExpr.expectedType(fromUnderscore = false) match {
      case Some(expectedType) if ScFunctionType.isFunctionType(expectedType) =>
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
        extends AbstractFixOnPsiElement(hint, expr){

  def doApplyFix(project: Project) {
    val scExpr = getElement
    if (!scExpr.isValid) return
    val newExpr = ScalaPsiElementFactory.createExpressionFromText(replacement, scExpr.getManager)
    scExpr.replaceExpression(newExpr, removeParenthesis = true)
  }
}
