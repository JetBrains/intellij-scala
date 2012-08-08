package org.jetbrains.plugins.scala
package codeInspection.cast

import lang.psi.types.result.TypingContext
import lang.psi.api.expr.{ScExpression, ScGenericCall}
import extensions.ElementText
import com.intellij.openapi.util.TextRange
import com.intellij.codeInspection.ex.ProblemDescriptorImpl
import codeInspection.{AbstractFix, AbstractInspection}
import com.intellij.openapi.project.Project
import com.intellij.codeInspection.{ProblemDescriptor, ProblemHighlightType, ProblemsHolder}

/**
 * Pavel Fatin
 */

class ScalaRedundantCastInspection extends AbstractInspection("Redundant cast") {
  def actionFor(holder: ProblemsHolder) = {
    case call: ScGenericCall =>
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

  class RemoveCastQuickFix(call: ScGenericCall, exp: ScExpression)
          extends AbstractFix("Remove Redundant Cast", call) {
    def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
      call.getParent.addBefore(exp, call)
      call.delete()
    }
  }
}


