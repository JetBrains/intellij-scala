package org.jetbrains.plugins.scala
package codeInspection
package parameters

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScBooleanLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.api.Boolean
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.project.ProjectContext

abstract class NameBooleanParametersInspectionBase extends LocalInspectionTool {

  import NameBooleanParametersInspectionBase._

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitMethodCallExpression(mc: ScMethodCall): Unit = {
        if (mc.isInScala3File) return // TODO Handle Scala 3 code (`using` arguments, etc.), SCL-19602
        val argList = mc.args
        if (argList.exprs.isEmpty) return
        if (isIgnoreSingleParameter && isSingleParamMethodCall(mc)) return
        for (expr <- argList.exprs) {
          expr match {
            case literal: ScBooleanLiteral if isArgForBooleanParam(expr, argList) &&
              addNameToArgumentsFix(literal).isDefined =>
              val message = ScalaInspectionBundle.message("name.boolean.params")
              val quickFix = new NameBooleanParametersQuickFix(message, literal)
              val descriptor = holder.getManager.createProblemDescriptor(
                expr,
                message,
                quickFix,
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                isOnTheFly
              )
              holder.registerProblem(descriptor)
            case _ =>
          }
        }
      }

      def isArgForBooleanParam(expr: ScExpression, argList: ScArgumentExprList): Boolean = {
        argList.parameterOf(expr).exists(isBooleanParam)
      }

      def isBooleanParam(p: Parameter): Boolean = {
        implicit val projectContext: ProjectContext = holder.getProject

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
              val targets = (Seq(srr.element) ++ srr.innerResolveResult.map(_.getElement)).filterByType[ScFunction]
              targets.exists(_.parameters.size == 1)
            }
          case _ => false
        }
      }

    }
  }

  def isIgnoreSingleParameter: Boolean

  def setIgnoreSingleParameter(value: Boolean): Unit

}

object NameBooleanParametersInspectionBase {

  private def addNameToArgumentsFix(literal: ScBooleanLiteral) =
    codeInsight.intention.addNameToArgumentsFix(literal, onlyBoolean = true)

  private class NameBooleanParametersQuickFix(@Nls name: String, literal: ScBooleanLiteral)
    extends AbstractFixOnPsiElement(name, literal) {

    override protected def doApplyFix(literal: ScBooleanLiteral)
                                     (implicit project: Project): Unit = {
      addNameToArgumentsFix(literal).foreach(_.apply())
    }
  }

}