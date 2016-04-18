package org.jetbrains.plugins.scala
package codeInspection.cast

import com.intellij.codeInspection.ex.ProblemDescriptorImpl
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnTwoPsiElements, AbstractInspection, ProblemsHolderExt}
import org.jetbrains.plugins.scala.extensions.ElementText
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScGenericCall}
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

/**
 * Pavel Fatin
 */

class ScalaRedundantCastInspection extends AbstractInspection("Redundant cast") {
  def actionFor(holder: ProblemsHolder) = {
    case call: ScGenericCall =>
      implicit val typeSystem = holder.typeSystem
      call.referencedExpr.children.toList match {
        case List(left: ScExpression, ElementText("."), ElementText("asInstanceOf")) =>
          for (actualType <- left.getType(TypingContext.empty).toOption;
               typeArgument <- call.arguments.headOption;
               castType <- typeArgument.getType(TypingContext.empty) if actualType.equiv(castType)) {

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
    def doApplyFix(project: Project) {
      val (call, expr) = (getFirstElement, getSecondElement)
      call.getParent.addBefore(expr, call)
      call.delete()
    }
  }
}


