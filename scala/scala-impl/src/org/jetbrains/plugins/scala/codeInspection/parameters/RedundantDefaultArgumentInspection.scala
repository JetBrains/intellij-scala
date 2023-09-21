package org.jetbrains.plugins.scala.codeInspection.parameters

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScExpression, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

class RedundantDefaultArgumentInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case ScMethodCall(referenceExpression: ScReferenceExpression, arguments: Seq[ScExpression]) =>
      referenceExpression.resolve() match {
        case function: ScFunction =>
          arguments.indices
            .filter(index => RedundantDefaultArgumentUtil.isRedundantArgumentAt(arguments, index, function.parameters))
            .foreach(index => registerProblem(arguments(index))(holder))
        case _ =>
      }
    case _ =>
  }

  private def registerProblem(expr: ScExpression)
                             (implicit holder: ProblemsHolder): Unit = {
    holder.registerProblem(expr, getDisplayName, new DeleteRedundantDefaultArgumentQuickFix(expr))
  }
}

class DeleteRedundantDefaultArgumentQuickFix(arg: ScExpression)
  extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("delete.redundant.default.argument"), arg) {

  override protected def doApplyFix(element: ScExpression)
                                   (implicit project: Project): Unit = {
    element.delete()
  }
}

object RedundantDefaultArgumentUtil {
  def isRedundantArgumentAt(arguments: Seq[ScExpression], index: Int, parameters: Seq[ScParameter]): Boolean = arguments(index) match {
    case expression: ScExpression if isAllArgumentsNamedAfterIndex(arguments, index) => expression match {
      case _: ScInterpolatedStringLiteral => false
      case literal: ScLiteral => parameters.isDefinedAt(index) && hasDefaultValue(parameters(index), literal)
      case namedArg@ScAssignment(_, Some(value: ScLiteral)) if namedArg.isNamedParameter => namedArg.referenceName match {
        case Some(argumentName: String) => parameters.exists(param => param.name == argumentName && hasDefaultValue(param, value))
        case _ => false
      }
      case _ => false
    }
    case _ => false
  }

  def hasDefaultValue(parameter: ScParameter, value: ScLiteral): Boolean = parameter.getDefaultExpression match {
    case Some(_: ScInterpolatedStringLiteral) => false
    case _ if value.isInstanceOf[ScInterpolatedStringLiteral] => false
    case Some(defaultValue: ScLiteral) if defaultValue.getValue != null => value.getValue == defaultValue.getValue
    case _ => false
  }

  def isAllArgumentsNamedAfterIndex(expressions: Seq[ScExpression], index: Int): Boolean = expressions.drop(index + 1).forall {
    case assign: ScAssignment if assign.isNamedParameter => true
    case _ => false
  }
}