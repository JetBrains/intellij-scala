package org.jetbrains.plugins.scala
package codeInspection.cast

import com.intellij.codeInspection.ex.ProblemDescriptorImpl
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnTwoPsiElements, AbstractInspection}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScPostfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.project.ProjectExt

/**
 * Pavel Fatin
 */

class ScalaRedundantConversionInspection extends AbstractInspection("Redundant conversion") {
  def actionFor(holder: ProblemsHolder) = {
    case element @ ScReferenceExpression.withQualifier(qualifier) && PsiReferenceEx.resolve(target) =>
      process(element, qualifier, target, qualifier.getTextLength, holder)
    case element @ ScPostfixExpr(operand, operator @ PsiReferenceEx.resolve(target)) =>
      process(element, operand, target, operator.getStartOffsetInParent, holder)
  }

  private def process(element: PsiElement, left: ScExpression, target: PsiElement, offset: Int, holder: ProblemsHolder)
                     (implicit typeSystem: TypeSystem = holder.getProject.typeSystem) {
    target match {
      case f: ScSyntheticFunction if f.name.startsWith("to") =>
        for (leftType <- left.getType(TypingContext.empty);
             conversionType = f.retType if leftType.equiv(conversionType))
          registerProblem(element, left, conversionType.presentableText, offset, holder)
      case f: PsiMethod if f.getName == "toString" &&
              f.getParameterList.getParametersCount == 0 &&
              (f.getTypeParameterList == null || f.getTypeParameterList.getTypeParameters.isEmpty) =>
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

  private class RemoveConversionQuickFix(element: PsiElement, expr: ScExpression)
          extends AbstractFixOnTwoPsiElements("Remove Redundant Conversion", element, expr) {
    def doApplyFix(project: Project) {
      val elem = getFirstElement
      val scExpr = getSecondElement
      elem.getParent.addBefore(scExpr, elem)
      elem.delete()
    }
  }
}


