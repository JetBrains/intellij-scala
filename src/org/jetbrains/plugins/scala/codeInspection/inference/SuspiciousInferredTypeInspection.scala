package org.jetbrains.plugins.scala
package codeInspection
package inference

import com.intellij.codeInspection._
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{Any, AnyVal, ScType}

class SuspiciousInferredTypeInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean =  false // jzaugg: Disabled by default while I try this out.

  override def getID: String = "SuspiciousInferredType"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    if (!holder.getFile.isInstanceOf[ScalaFile]) return new PsiElementVisitor {}
    new ScalaElementVisitor {
      override def visitReferenceExpression(ref: ScReferenceExpression) {
        visitExpression(ref)
      }

      override def visitExpression(expr: ScExpression) {
        val exprResultUsed = expr.getContext match {
          case blk: ScBlock if blk.lastExpr.forall(_ != expr) => false
          case _ => true
        }
        if (exprResultUsed && expr.expectedType().isEmpty) {
          expr.getType(TypingContext.empty) match {
            case Success(inferredType, _) if inferredType == AnyVal || inferredType == Any =>
              val presentable = ScType.presentableText(inferredType)
              holder.registerProblem(holder.getManager.
                      createProblemDescriptor(expr, ScalaBundle.message("suspicicious.inference", presentable),
                Array[LocalQuickFix](), ProblemHighlightType.WEAK_WARNING))
              // We don't call super.visitExpresion() to recurse further, as we couldn't annotate a sub-expression in an visually appealling manner.
              return
            case _ =>
          }
        }
        super.visitExpression(expr)
      }
    }
  }
}