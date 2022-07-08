package org.jetbrains.plugins.scala
package codeInspection.cast

import com.intellij.codeInspection.ex.ProblemDescriptorImpl
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnTwoPsiElements, AbstractInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.{ElementText, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScGenericCall}
import org.jetbrains.plugins.scala.lang.psi.types.{ScTypeExt, TypePresentationContext}

import scala.annotation.nowarn

@nowarn("msg=" + AbstractInspection.DeprecationText)
class ScalaRedundantCastInspection extends AbstractInspection(ScalaInspectionBundle.message("display.name.redundant.cast")) {

  override protected def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Unit] = {
    case call: ScGenericCall =>
      implicit val tpc: TypePresentationContext = TypePresentationContext(call)

      call.referencedExpr.children.toList match {
        case List(left: ScExpression, ElementText("."), ElementText("asInstanceOf")) =>
          for (actualType <- left.`type`().toOption;
               typeArgument <- call.arguments.headOption;
               castType <- typeArgument.`type`().toOption if actualType.widen.equiv(castType)) {

            val descriptor = {
              val range = new TextRange(left.getTextLength, call.getTextLength)

              val message = ScalaInspectionBundle.message("casting.left.to.right.is.redundant", left.getText, castType.presentableText)

              new ProblemDescriptorImpl(call, call, message, Array(new RemoveCastQuickFix(call, left)),
                ProblemHighlightType.LIKE_UNUSED_SYMBOL, false, range, null, false)
            }

            holder.registerProblem(descriptor)
          }
        case _ =>
      }
  }

  class RemoveCastQuickFix(c: ScGenericCall, e: ScExpression)
          extends AbstractFixOnTwoPsiElements(ScalaInspectionBundle.message("remove.redundant.cast"), c, e) {


    override protected def doApplyFix(call: ScGenericCall, expr: ScExpression)
                                     (implicit project: Project): Unit = {
      call.getParent.addBefore(expr, call)
      call.delete()
    }
  }
}


