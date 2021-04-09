package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, result}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.CollectMethodsProcessor

/**
  * Pavel Fatin
  */
final class JavaAccessorEmptyParenCallInspection extends AbstractRegisteredInspection {

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager,
                                           isOnTheFly: Boolean): Option[ProblemDescriptor] =
    element match {
      case (place: ScReferenceExpression) childOf (call: ScMethodCall) if call.argumentExpressions.isEmpty =>
        import JavaAccessorEmptyParenCallInspection._
        val problemExists = place match {
          case _ if call.getParent.is[ScMethodCall] => false
          case Resolved(resolveResult@ScalaResolveResult(method: PsiMethod, _))
            if quickfix.isAccessor(method) &&
              isNotOverloadedMethod(place, resolveResult.fromType) =>
            hasSameType(call, place)
          case _ => false
        }

        if (problemExists) super.problemDescriptor(place.nameId, createQuickFix(call), descriptionTemplate, highlightType)
        else None
      case _ => None
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

  private def createQuickFix(call: ScMethodCall): Option[AbstractFixOnPsiElement[ScMethodCall]] = {
    val quickFix = new AbstractFixOnPsiElement(
      ScalaInspectionBundle.message("remove.call.parentheses"),
      call
    ) {
      override protected def doApplyFix(call: ScMethodCall)(implicit project: Project): Unit = {
        val text = call.getInvokedExpr.getText
        val replacement = ScalaPsiElementFactory.createExpressionFromText(text)
        call.replace(replacement)
      }
    }

    Some(quickFix)
  }

  private[this] def processType(`type`: ScType,
                                place: ScReferenceExpression): Set[ScalaResolveResult] = {
    val processor = new CollectMethodsProcessor(place, place.refName)
    processor.processType(`type`, place)
    processor.candidatesS
  }
}
