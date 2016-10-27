package org.jetbrains.plugins.scala.codeInspection.parameters

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignStmt, ScExpression, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

class RedundantDefaultArgumentInspection extends AbstractInspection("RedundantDefaultArgumentInspection", "Argument duplicates corresponding parameter default value") {
  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case expr@ScMethodCall(referenceExpression: ScReferenceExpression, arguments: Seq[ScExpression]) =>
      referenceExpression.resolve() match {
        case function: ScFunction =>
          arguments.indices
            .filter(index => RedundantDefaultArgumentUtil.isRedundantArgumentAt(arguments, index, function.parameters))
            .foreach(index => registerProblem(holder, arguments(index)))
        case _ =>
      }
    case _ =>
  }

  private def registerProblem(holder: ProblemsHolder, expr: ScExpression) = {
    holder.registerProblem(expr, getDisplayName, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new DeleteRedundantDefaultArgumentQuickFix(expr))
  }
}

class DeleteRedundantDefaultArgumentQuickFix(arg: ScExpression) extends AbstractFixOnPsiElement("Delete redundant default argument", arg) {
  override def doApplyFix(project: Project) = {
    val element = getElement
    if (element.isValid) RedundantDefaultArgumentUtil.deleteFromCommaSeparatedList(element)
  }
}

object RedundantDefaultArgumentUtil {
  def isRedundantArgumentAt(arguments: Seq[ScExpression], index: Int, parameters: Seq[ScParameter]): Boolean = arguments(index) match {
    case expression: ScExpression if isAllArgumentsNamedAfterIndex(arguments, index) => expression match {
      case literal: ScLiteral => parameters.isDefinedAt(index) && hasDefaultValue(parameters(index), literal)
      case namedArgumentAssign@ScAssignStmt(_, Some(value: ScLiteral)) => namedArgumentAssign.assignName match {
        case Some(argumentName: String) => parameters.exists(param => param.name == argumentName && hasDefaultValue(param, value))
        case _ => false
      }
      case _ => false
    }
    case _ => false
  }

  def hasDefaultValue(parameter: ScParameter, value: ScLiteral): Boolean = parameter.getDefaultExpression match {
    case Some(defaultValue: ScLiteral) if defaultValue.getValue != null => value.getValue == defaultValue.getValue
    case _ => false
  }

  def isAllArgumentsNamedAfterIndex(expressions: Seq[ScExpression], index: Int): Boolean = expressions.drop(index + 1).forall {
    case namedArgumentAssign: ScAssignStmt => true
    case _ => false
  }

  def deleteFromCommaSeparatedList(element: ScalaPsiElement): Unit = {
    element.getPrevSiblingNotWhitespaceComment match {
      case prev: PsiElement if prev.textMatches(",") => prev.delete()
      case _ => element.getNextSiblingNotWhitespaceComment match {
        case next if next.textMatches(",") => next.delete()
        case _ =>
      }
    }
    element.delete()
  }
}