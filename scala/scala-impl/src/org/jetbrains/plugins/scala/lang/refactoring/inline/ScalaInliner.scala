package org.jetbrains.plugins.scala.lang.refactoring.inline

import com.intellij.lang.refactoring.InlineHandler
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.plugins.scala.extensions.{Parent, TraversableExt, childOf}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createTypeElementFromText}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.highlightOccurrences

import scala.collection.JavaConverters._

class ScalaInliner(replacementValue: String) extends InlineHandler.Inliner {

  override def getConflicts(reference: PsiReference, referenced: PsiElement): MultiMap[PsiElement, String] = MultiMap.empty()

  override def inlineUsage(usage: UsageInfo, referenced: PsiElement): Unit = {
    val newValue = usage.getReference.getElement match {
      case Parent(call: ScMethodCall) if call.argumentExpressions.isEmpty =>
        replace(call)
      case Parent(call: ScMethodCall) =>
        replaceFunctionCall(call, referenced)
      case expr: ScExpression =>
        replace(expr)
      case Parent(typeElement: ScTypeElement) =>
        replace(typeElement)
      case _ => None
    }
    newValue.foreach(postProcess)
  }

  private def replaceFunctionCall(call: ScMethodCall, referenced: PsiElement): Option[ScExpression] = {
    referenced match {
      case function: ScFunctionDefinition =>
        val replacement = replacementForCall(call, function)
        Some(call.replaceExpression(replacement, removeParenthesis = true))
      case _ =>
        None
    }
  }

  private def replacementForCall(call: ScMethodCall, function: ScFunctionDefinition): ScExpression = {
    val funBodyCopy = function.body match {
      case Some(body) =>
        ScalaPsiElementFactory.createExpressionWithContextFromText(body.getText, function, body)
      case _ => return call
    }
    val paramToReplacement = call.matchedParameters.flatMap {
      case (expr, p) => p.paramInCode.map((_, expr))
    }

    val scope = new LocalSearchScope(funBodyCopy)
    var result: ScExpression = funBodyCopy
    for {
      (parameter, replacement) <- paramToReplacement
      reference <- ReferencesSearch.search(parameter, scope).asScala.filterBy[ScReferenceExpression]
    } {
      if (reference == funBodyCopy)
        result = replacement
      else
        reference.replaceExpression(replacement, removeParenthesis = true)
    }

    result
  }

  private def replace(expr: ScExpression): Some[ScExpression] = {
    import expr.projectContext

    val oldValue = expr match {
      case _ childOf (_: ScInterpolatedStringLiteral) =>
        s"{" + replacementValue + "}"
      case _ =>
        replacementValue
    }
    Some(expr.replaceExpression(createExpressionFromText(oldValue), removeParenthesis = true))
  }

  private def replace(te: ScTypeElement): Some[PsiElement] = {
    import te.projectContext

    Some(te.replace(createTypeElementFromText(replacementValue)))
  }

  private def postProcess(newValue: PsiElement) = {
    val project = newValue.getProject
    val editor = FileEditorManager.getInstance(project).getSelectedTextEditor
    highlightOccurrences(Seq(newValue))(project, editor)
    CodeStyleManager.getInstance(project).reformatRange(newValue.getContainingFile, newValue.getTextRange.getStartOffset - 1,
      newValue.getTextRange.getEndOffset + 1) //to prevent situations like this 2 ++2 (+2 was inlined)
  }
}
