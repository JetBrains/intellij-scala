package org.jetbrains.plugins.scala
package codeInspection.cast

import com.intellij.codeInspection.ex.ProblemDescriptorImpl
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnTwoPsiElements, AbstractInspection}
import org.jetbrains.plugins.scala.extensions.{ElementText, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScGenericCall}
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt

/**
 * Pavel Fatin
 */
class ScalaRedundantCastInspection extends AbstractInspection("Redundant cast") {

  override protected def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case call: ScGenericCall =>


      call.referencedExpr.children.toList match {
        case List(left: ScExpression, ElementText("."), ElementText("asInstanceOf")) =>
          for (actualType <- left.`type`().toOption;
               typeArgument <- call.arguments.headOption;
               castType <- typeArgument.`type`().toOption if actualType.equiv(castType)) {

            val descriptor = {
              val range = new TextRange(left.getTextLength, call.getTextLength)

              val message = "Casting '%s' to '%s' is redundant".format(left.getText, castType.presentableText)

              new ProblemDescriptorImpl(call, call, message, Array(new RemoveCastQuickFix(call, left)),
                ProblemHighlightType.LIKE_UNUSED_SYMBOL, false, range, null, false)
            }

            holder.registerProblem(descriptor)
          }
        case _ =>
      }
  }

  class RemoveCastQuickFix(c: ScGenericCall, e: ScExpression)
          extends AbstractFixOnTwoPsiElements("Remove Redundant Cast", c, e) {


    override protected def doApplyFix(call: ScGenericCall, expr: ScExpression)
                                     (implicit project: Project): Unit = {
      call.getParent.addBefore(expr, call)
      call.delete()
    }
  }
}


