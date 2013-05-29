package org.jetbrains.plugins.scala
package codeInspection.parameters

import lang.psi.api.ScalaElementVisitor
import codeInspection.InspectionBundle
import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.util.IntentionUtils
import lang.psi.types.result.TypingContext
import com.intellij.psi.{PsiElementVisitor, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import com.intellij.psi.util.PsiTreeUtil
import collection.Seq
import lang.psi.types.nonvalue.Parameter
import lang.psi.api.base.ScLiteral
import lang.psi.api.expr._
import com.intellij.openapi.diagnostic.Logger
import javax.swing.{JCheckBox, JPanel, JComponent}
import java.awt._
import javax.swing.event.{ChangeListener, ChangeEvent}
import scala.Some
import lang.psi.api.statements.ScFunction
import com.intellij.ide.util.PropertiesComponent
import org.jdom.Element
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel

/**
 * @author Ksenia.Sautina
 * @since 5/10/12
 */

abstract class NameBooleanParametersInspectionStub extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitMethodCallExpression(mc: ScMethodCall) {
        if (mc == null || mc.args == null || mc.args.exprs.isEmpty) return
        mc.getInvokedExpr match {
          case ref: ScReferenceExpression => ref.resolve() match {
            case fun: ScFunction =>
              //todo
              if (fun.name.startsWith("set") && mc.args.exprs.size == 1 && isBooleanType(mc.args.exprs(0)) &&
                      getIgnoreSetters) return
            case _ =>
          }
          case _ =>
        }
        for (expr <- mc.args.exprs) {
          expr match {
            case literal: ScLiteral if isBooleanType(expr) &&
                    IntentionUtils.check(literal, onlyBoolean = true).isDefined &&
                    (expr.getNode.getFirstChildNode.getElementType == ScalaTokenTypes.kTRUE ||
                    expr.getNode.getFirstChildNode.getElementType == ScalaTokenTypes.kFALSE) =>
              holder.registerProblem(holder.getManager.createProblemDescriptor(expr,
                InspectionBundle.message("name.boolean"),
                new NameBooleanParametersQuickFix(mc, literal),
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
                  case (expr, Some(param)) => {
                    val paramInCode = param.paramInCode.getOrElse(null)
                    if (paramInCode == null) return false
                    if (!paramInCode.isValid) return false //todo: find why it can be invalid?
                    val realParameterType = paramInCode.getRealParameterType(TypingContext.empty).getOrElse(null)
                    if (realParameterType == null) return false
                    else if (realParameterType.canonicalText == "Boolean") return true
                    else return false
                  }
                  case _ => return false
                }
            }
          case None => false
        }
      }
    }
  }

  def getIgnoreSetters: Boolean
  def setIgnoreSetters(value: Boolean)

}

object NameBooleanParametersInspectionStub {
  private val LOG = Logger.getInstance(getClass)
}
