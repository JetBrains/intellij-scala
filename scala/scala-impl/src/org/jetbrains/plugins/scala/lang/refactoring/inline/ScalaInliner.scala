package org.jetbrains.plugins.scala.lang.refactoring.inline

import com.intellij.lang.refactoring.InlineHandler
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.plugins.scala.extensions.{Parent, childOf}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignStmt, ScExpression, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createTypeElementFromText}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.highlightOccurrences
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.JavaConverters._

class ScalaInliner(replacementValue: String) extends InlineHandler.Inliner {

  override def inlineUsage(usage: UsageInfo, referenced: PsiElement): Unit = {
    usage.getReference.getElement match {
      case Parent(call: ScMethodCall) if call.argumentExpressions.isEmpty => replace(call)
      case Parent(call: ScMethodCall) => replaceFunctionCall(call, referenced)
      case e: ScExpression => replace(e)
      case Parent(reference: ScTypeElement) => replace(reference)
      case _ => ()
    }
  }

  private def replaceFunctionCall(call: ScMethodCall, referenced: PsiElement): Unit = {
    referenced.copy match {
      case function: ScFunctionDefinition =>
        replaceReferencesInFunctionBody(call, function)
        function.body.foreach { body =>
          val newValue = call match {
            case expression: ScExpression =>
              expression.replaceExpression(body, removeParenthesis = true)
            case _ => call.replace(body)
          }

          postProcess(newValue)
        }
      case _ => ()
    }
  }

  private def replaceReferencesInFunctionBody(call: ScMethodCall, function: ScFunctionDefinition): Unit = {
    val scope = new LocalSearchScope(function)
    resolveParametersReplacements(call, function).foreach {
      case (parameter, replacement) =>
        val references = ReferencesSearch.search(parameter, scope).asScala
        references.foreach(_.getElement.replace(replacement))
    }
  }

  private def resolveParametersReplacements(call: ScMethodCall, function: ScFunctionDefinition) = {
    val parameters = function.parameters
    val paramsByName = parameters.map(parmeter => parmeter.name -> parmeter).toMap
    val paramNameToParameterWithReplacement = (parameters zip call.argumentExpressions).map {
      case (_, ScAssignStmt(argumentName, Some(argumentValue))) =>
        paramsByName(argumentName.getText) -> argumentValue

      case pair => pair
    }.map {
      case (parameter, replacement) =>
        parameter.name -> (parameter, replacement)
    }.toMap

    val missingParametersWithDefaultValues =
      parameters.filterNot(p => paramNameToParameterWithReplacement.keySet.contains(p.name))
        .map(p => p -> (
            p.getDefaultExpression match {
              case Some(defaultValue) => defaultValue
              case None => p
            }
          )
        )

    paramNameToParameterWithReplacement.values ++ missingParametersWithDefaultValues
  }

  override def getConflicts(reference: PsiReference, referenced: PsiElement): MultiMap[PsiElement, String] =
    new com.intellij.util.containers.MultiMap[PsiElement, String]()

  private def replace(replacement: ScalaPsiElement): PsiElement = {
    implicit val projectContext: ProjectContext = replacement.projectContext
    val newValue = replacement match {
      case expression: ScExpression =>
        val oldValue = expression match {
          case _ childOf (_: ScInterpolatedStringLiteral) =>
            s"{" + replacementValue + "}"
          case _ =>
            replacementValue
        }
        expression.replaceExpression(createExpressionFromText(oldValue), removeParenthesis = true)
      case _: ScTypeElement =>
        replacement.replace(createTypeElementFromText(replacementValue))
    }

    postProcess(newValue)
  }

  private def postProcess(newValue: PsiElement) = {
    val project = newValue.getProject
    val editor = FileEditorManager.getInstance(project).getSelectedTextEditor
    highlightOccurrences(Seq(newValue))(project, editor)
    CodeStyleManager.getInstance(project).reformatRange(newValue.getContainingFile, newValue.getTextRange.getStartOffset - 1,
      newValue.getTextRange.getEndOffset + 1) //to prevent situations like this 2 ++2 (+2 was inlined)
  }
}
