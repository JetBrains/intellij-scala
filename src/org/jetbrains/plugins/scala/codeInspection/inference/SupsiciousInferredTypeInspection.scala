package org.jetbrains.plugins.scala
package codeInspection
package inference

import collection.mutable.ArrayBuffer
import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, Any, AnyVal}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockExpr, ScExpression}
import com.intellij.psi.{PsiElementVisitor, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaRecursiveElementVisitor, ScalaFile}

class SupsiciousInferredTypeInspection extends LocalInspectionTool {
  def getGroupDisplayName: String = InspectionsUtil.SCALA

  def getDisplayName: String = "Suspicious Inferred Type"

  def getShortName: String = "Suspicious Inferred Type"

  override def isEnabledByDefault: Boolean =  false // jzaugg: Disabled by default while I try this out.

  override def getStaticDescription: String = "Detects inferred types of Any or AnyVal"

  override def getID: String = "SuspiciousInferredType"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    if (!holder.getFile.isInstanceOf[ScalaFile]) return new PsiElementVisitor {}
    new ScalaElementVisitor {
      override def visitExpression(expr: ScExpression): Unit = {
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
                Array[LocalQuickFix](), ProblemHighlightType.INFO))
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