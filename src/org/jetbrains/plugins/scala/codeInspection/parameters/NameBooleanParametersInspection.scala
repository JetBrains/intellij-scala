package org.jetbrains.plugins.scala
package codeInspection.parameters

import lang.psi.api.ScalaElementVisitor
import codeInspection.InspectionBundle
import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.util.IntentionUtils
import lang.psi.types.result.TypingContext
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.extensions._
import com.intellij.psi.util.PsiTreeUtil
import collection.Seq
import lang.psi.types.nonvalue.Parameter
import lang.psi.api.base.ScLiteral
import lang.psi.api.expr._
import com.intellij.psi.PsiElement

/**
 * @author Ksenia.Sautina
 * @since 5/10/12
 */

class NameBooleanParametersInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitMethodCallExpression(mc: ScMethodCall) {
        if (mc == null || mc.args == null || mc.args.exprs.isEmpty) return
        for (expr <- mc.args.exprs) {
          if (expr.isInstanceOf[ScLiteral] && isBooleanType(expr) &&
                  IntentionUtils.check(expr.asInstanceOf[ScLiteral]).isDefined &&
                  (expr.getNode.getFirstChildNode.getElementType == ScalaTokenTypes.kTRUE ||
                          expr.getNode.getFirstChildNode.getElementType == ScalaTokenTypes.kFALSE)) {
            holder.registerProblem(holder.getManager.createProblemDescriptor(expr,
              InspectionBundle.message("name.boolean"),
              new NameBooleanParametersQuickFix(mc, expr.asInstanceOf[ScLiteral]),
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly))
          }
        }
      }

      def isBooleanType(element: PsiElement): Boolean = {
        val containingArgList: Option[ScArgumentExprList] = element.parents.collectFirst {
          case al: ScArgumentExprList if !al.isBraceArgs => al
        }
        containingArgList match {
          case Some(al) =>
            val index = al.exprs.indexWhere(argExpr => PsiTreeUtil.isAncestor(argExpr, element, false))
            index match {
              case -1 => None
              case i =>
                val argExprsToNamify = al.exprs.drop(index)
                val argsAndMatchingParams: Seq[(ScExpression, Option[Parameter])] = argExprsToNamify.map {
                  arg => (arg, al.parameterOf(arg))
                }
                argsAndMatchingParams.exists {
                  case (expr, Some(param)) => {
                    val paramInCode = param.paramInCode.getOrElse(null)
                    if (paramInCode != null &&
                            paramInCode.getRealParameterType(TypingContext.empty).get.canonicalText == "Boolean")
                      return true
                    else return false
                  }
                  case _ => return false
                }
            }
        }
        false
      }
    }
  }

}
