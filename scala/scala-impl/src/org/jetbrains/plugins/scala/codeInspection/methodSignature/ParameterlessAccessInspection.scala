package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

import scala.annotation.tailrec

sealed abstract class ParameterlessAccessInspection extends AbstractRegisteredInspection {

  import ParameterlessAccessInspection._

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager,
                                           isOnTheFly: Boolean): Option[ProblemDescriptor] =
    element match {
      case _ if !isValid(element) => None
      case reference@ScReferenceExpression(method: PsiMethod) if isValid(method) =>
        val maybeTargetExpression = reference.getParent match {
          case parent if !isFixable(parent) => None
          case call: ScGenericCall if !findCall(call) => Some(call)
          case _: ScGenericCall => None
          case _ => Some(reference)
        }

        for {
          targetExpression <- maybeTargetExpression
          e <- collect(targetExpression, reference)

          quickfix = createQuickFix(e)
          problemDescriptor <- super.problemDescriptor(reference.nameId, Some(quickfix), descriptionTemplate, highlightType)
        } yield problemDescriptor
      case _ => None
    }

  protected def isValid(element: PsiElement): Boolean = element.isValid

  protected def isValid(method: PsiMethod): Boolean

  protected def isFixable(parent: PsiElement): Boolean = parent match {
    case _: ScMethodCall |
         _: ScInfixExpr |
         _: ScUnderscoreSection => false
    case _ => true
  }

  protected def collect(expression: ScExpression,
                        reference: ScReferenceExpression): Option[ScExpression]
}

object ParameterlessAccessInspection {

  final class JavaMutator extends ParameterlessAccessInspection {

    override protected def collect(expression: ScExpression,
                                   reference: ScReferenceExpression): Option[ScExpression] = reference match {
      case HasFunctionType(_) => None
      case _ => Some(expression)
    }

    override protected def isValid(method: PsiMethod): Boolean = quickfix.isMutator(method)
  }

  // TODO: this should be an ERROR in Scala3, not a WARNING
  //  for example this in an error:
  //  def m00() = "00"
  //  val f002 = m00
  final class EmptyParenMethod extends ParameterlessAccessInspection {

    // might have been eta-expanded to () => A, so don't worn.
    // this avoids false positives. To be more accurate, we would need an 'etaExpanded'
    // flag in ScalaResolveResult.
    override protected def collect(expression: ScExpression,
                                   reference: ScReferenceExpression): Option[ScExpression] = expression match {
      case HasFunctionType(Seq()) => None
      case _                      => Some(reference)
    }

    override protected def isValid(element: PsiElement): Boolean =
      super.isValid(element) && IntentionAvailabilityChecker.checkInspection(this, element)

    override protected def isValid(method: PsiMethod): Boolean = method match {
      case function: ScFunction =>
        function.isValid && !function.isInCompiledFile && function.isEmptyParen
      case _ => false
    }

    override protected def isFixable(parent: PsiElement): Boolean = parent match {
      case _: ScPrefixExpr => false
      case _ => super.isFixable(parent)
    }
  }

  @tailrec
  private def findCall(element: PsiElement): Boolean = element.getContext match {
    case _: ScMethodCall => true
    case expression: ScParenthesisedExpr => findCall(expression)
    case call: ScGenericCall => findCall(call)
    case _ => false
  }

  private def createQuickFix(expression: ScExpression): AbstractFixOnPsiElement[ScExpression] = new AbstractFixOnPsiElement(
    ScalaInspectionBundle.message("add.call.parentheses"),
    expression
  ) {
    override protected def doApplyFix(expression: ScExpression)(implicit project: Project): Unit = {
      val target = expression.getParent match {
        case postfix: ScPostfixExpr => postfix
        case call: ScGenericCall => call
        case _ => expression
      }

      val replacement = ScalaPsiElementFactory.createExpressionFromText(s"${target.getText}()")
      target.replace(replacement)
    }
  }

  private object HasFunctionType {

    def unapply(expression: ScExpression): Option[Seq[ScType]] = expression match {
      case result.Typeable(api.FunctionType(_, seq)) => Some(seq)
      case _ => None
    }
  }

}
