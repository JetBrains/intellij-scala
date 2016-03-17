package org.jetbrains.plugins.scala
package codeInspection.parameters

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScBooleanLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.{ScTypeExt, Boolean}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.IntentionUtils

import scala.collection.Seq

/**
 * @author Ksenia.Sautina
 * @since 5/10/12
 */

abstract class NameBooleanParametersInspectionBase extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    implicit val typeSystem = holder.getProject.typeSystem
    new ScalaElementVisitor {
      override def visitMethodCallExpression(mc: ScMethodCall) {
        if (mc == null || mc.args == null || mc.args.exprs.isEmpty) return
        if (isIgnoreSingleParameter && isSingleParamMethodCall(mc)) return
        val argList = mc.args
        for (expr <- argList.exprs) {
          expr match {
            case lit @ ScBooleanLiteral(_) if isArgForBooleanParam(expr, argList) &&
                    IntentionUtils.addNameToArgumentsFix(expr, onlyBoolean = true).isDefined =>
              val descriptor = holder.getManager.createProblemDescriptor(expr, InspectionBundle.message("name.boolean"),
                new NameBooleanParametersQuickFix(lit), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly)
              holder.registerProblem(descriptor)
            case _ =>
          }
        }
      }

      def isArgForBooleanParam(expr: ScExpression, argList: ScArgumentExprList): Boolean = {
        argList.parameterOf(expr).exists(isBooleanParam)
      }

      def isBooleanParam(p: Parameter): Boolean = {
        if (p.isRepeated) false
        else {
          val typeElem = p.paramInCode.flatMap(_.typeElement)
          typeElem.exists(_.calcType.equiv(Boolean))
        }
      }

      def isSingleParamMethodCall(mc: ScMethodCall): Boolean = {
        mc.getInvokedExpr match {
          case ref: ScReferenceExpression =>
            ref.bind().exists { srr =>
              val targets = (Seq(srr.element) ++ srr.innerResolveResult.map(_.getElement)).filterBy(classOf[ScFunction])
              targets.exists(_.parameters.size == 1)
            }
          case _ => false
        }
      }

    }
  }

  def isIgnoreSingleParameter: Boolean

  def setIgnoreSingleParameter(value: Boolean)

}
