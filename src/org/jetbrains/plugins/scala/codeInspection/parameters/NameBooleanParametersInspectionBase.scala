package org.jetbrains.plugins.scala
package codeInspection.parameters

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.util.IntentionUtils

import scala.collection.Seq

/**
 * @author Ksenia.Sautina
 * @since 5/10/12
 */

abstract class NameBooleanParametersInspectionBase extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitMethodCallExpression(mc: ScMethodCall) {
        if (mc == null || mc.args == null || mc.args.exprs.isEmpty) return
        mc.getInvokedExpr match {
          case ref: ScReferenceExpression => ref.resolve() match {
            case fun: ScFunction if fun.parameters.size == 1 => return
            case _ =>
          }
          case _ =>
        }
        for (expr <- mc.args.exprs) {
          expr match {
            case literal: ScLiteral if isBooleanType(expr) &&
                    IntentionUtils.addNameToArgumentsFix(literal, onlyBoolean = true).isDefined &&
                    (expr.getNode.getFirstChildNode.getElementType == ScalaTokenTypes.kTRUE ||
                    expr.getNode.getFirstChildNode.getElementType == ScalaTokenTypes.kFALSE) =>
              holder.registerProblem(holder.getManager.createProblemDescriptor(expr,
                InspectionBundle.message("name.boolean"),
                new NameBooleanParametersQuickFix(literal),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly))
            case _ =>
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
              case -1 => false
              case i =>
                val argExprsToNamify = al.exprs.drop(index)
                val argsAndMatchingParams: Seq[(ScExpression, Option[Parameter])] = argExprsToNamify.map {
                  arg => (arg, al.parameterOf(arg))
                }
                argsAndMatchingParams.exists {
                  case (expr, Some(param)) =>
                    val paramInCode = param.paramInCode.orNull
                    if (paramInCode == null) return false
                    if (!paramInCode.isValid) return false //todo: find why it can be invalid?
                    val realParameterType = paramInCode.getRealParameterType(TypingContext.empty).getOrElse(null)
                    if (realParameterType == null) return false
                    else if (realParameterType.canonicalText == "Boolean") return true
                    else return false
                  case _ => return false
                }
            }
          case None => false
        }
      }
    }
  }

  def isIgnoreSingleParameter: Boolean
  def setIgnoreSingleParameter(value: Boolean)

}
