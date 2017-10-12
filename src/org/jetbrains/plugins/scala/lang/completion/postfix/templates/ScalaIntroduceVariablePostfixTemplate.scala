package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.SelectorType._
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.{AncestorSelector, SelectorConditions}
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaIntroduceVariableHandler

/**
 * @author Roman.Shein
 * @since 11.09.2015.
 */
class ScalaIntroduceVariablePostfixTemplate extends PostfixTemplateWithExpressionSelector("var", "val name = expr",
  new AncestorSelector(SelectorConditions.ANY_EXPR, All)) {

  override def expandForChooseExpression(expression: PsiElement, editor: Editor): Unit = {
    val range = expression.getTextRange
    val startOffset = range.getStartOffset
    val endOffset = range.getEndOffset

    editor.getSelectionModel.setSelection(startOffset, endOffset)
    new ScalaIntroduceVariableHandler()
      .invokeExpression(expression.getContainingFile, startOffset, endOffset)(expression.getProject, editor)
  }
}
