package org.jetbrains.plugins.scala
package codeInspection.cast

import lang.psi.types.result.TypingContext
import com.intellij.openapi.util.TextRange
import com.intellij.codeInspection.ex.ProblemDescriptorImpl
import codeInspection.{AbstractFix, AbstractInspection}
import com.intellij.openapi.project.Project
import com.intellij.codeInspection.{ProblemDescriptor, ProblemHighlightType, ProblemsHolder}
import lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import lang.psi.api.expr.{ScPostfixExpr, ScReferenceExpression, ScExpression}
import extensions._
import com.intellij.psi.{PsiElement, PsiMethod}

/**
 * Pavel Fatin
 */

class ScalaRedundantConversionInspection extends AbstractInspection("Redundant conversion") {
  def actionFor(holder: ProblemsHolder) = {
    case element @ ScReferenceExpression.qualifier(qualifier) && PsiReferenceEx.resolve(target) =>
      process(element, qualifier, target, qualifier.getTextLength, holder)
    case element @ ScPostfixExpr(operand, operator @ PsiReferenceEx.resolve(target)) =>
      process(element, operand, target, operator.getStartOffsetInParent, holder)
  }

  private def process(element: PsiElement, left: ScExpression, target: PsiElement, offset: Int, holder: ProblemsHolder) {
    target match {
      case f: ScSyntheticFunction if f.name.startsWith("to") =>
        for (leftType <- left.getType(TypingContext.empty);
             conversionType = f.retType if leftType.equiv(conversionType))
          registerProblem(element, left, conversionType.presentableText, offset, holder)
      case f: PsiMethod if f.getName == "toString" &&
              f.getParameterList.getParametersCount == 0 &&
              f.getTypeParameterList.getTypeParameters.length == 0 =>
        for (leftType <- left.getType(TypingContext.empty) if leftType.canonicalText == "_root_.java.lang.String")
          registerProblem(element, left, "java.lang.String", offset, holder)
      case _ =>
    }
  }

  private def registerProblem(element: PsiElement, left: ScExpression, conversionType: String,
                      offset: Int, holder: ProblemsHolder) {
    val descriptor = {
      val range = new TextRange(offset, element.getTextLength)

      val message = "Casting '%s' to '%s' is redundant".format(left.getText, conversionType)

      new ProblemDescriptorImpl(element, element, message, Array(new RemoveConversionQuickFix(element, left)),
        ProblemHighlightType.LIKE_UNUSED_SYMBOL, false, range, null, false)
    }

    holder.registerProblem(descriptor)
  }

  private class RemoveConversionQuickFix(element: PsiElement, exp: ScExpression)
          extends AbstractFix("Remove Redundant Conversion", element) {
    def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
      element.getParent.addBefore(exp, element)
      element.delete()
    }
  }
}


