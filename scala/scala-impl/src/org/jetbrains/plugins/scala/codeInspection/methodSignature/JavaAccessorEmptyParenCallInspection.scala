package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.codeInspection.methodSignature.JavaAccessorEmptyParenCallInspection.{createQuickFix, hasSameType, isNotOverloadedMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, result}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.CollectMethodsProcessor

final class JavaAccessorEmptyParenCallInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case (place: ScReferenceExpression) childOf (call: ScMethodCall) if call.argumentExpressions.isEmpty =>
      place match {
        case _ if call.getParent.is[ScMethodCall] =>
        case Resolved(resolveResult@ScalaResolveResult(method: PsiMethod, _))
          if quickfix.isAccessor(method) &&
            isNotOverloadedMethod(place, resolveResult.fromType) =>
          if (hasSameType(call, place)) {
            holder.registerProblem(place.nameId, getDisplayName, createQuickFix(call))
          }
        case _ =>
      }
    case _ =>
  }
}

object JavaAccessorEmptyParenCallInspection {

  private def isNotOverloadedMethod(place: ScReferenceExpression,
                                    fromType: Option[ScType]) =
    fromType.map(processType(_, place))
      .forall(_.size <= 1)

  private def hasSameType(call: ScMethodCall,
                          place: ScReferenceExpression) = (call, place) match {
    case (result.Typeable(left), result.Typeable(right)) => left.equiv(right)
    case _ => false
  }

  private def createQuickFix(call: ScMethodCall): AbstractFixOnPsiElement[ScMethodCall] =
    new AbstractFixOnPsiElement(ScalaInspectionBundle.message("remove.call.parentheses"), call) {
      override protected def doApplyFix(call: ScMethodCall)(implicit project: Project): Unit = {
        val text = call.getInvokedExpr.getText
        val replacement = ScalaPsiElementFactory.createExpressionFromText(text)
        call.replace(replacement)
      }
    }

  private[this] def processType(`type`: ScType,
                                place: ScReferenceExpression): Set[ScalaResolveResult] = {
    val processor = new CollectMethodsProcessor(place, place.refName)
    processor.processType(`type`, place)
    processor.candidatesS
  }
}
