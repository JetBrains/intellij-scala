package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.{SelectorConditions, AncestorSelector}
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.SelectorType._
import org.jetbrains.plugins.scala.lang.refactoring.introduceField.ScalaIntroduceFieldFromExpressionHandler

/**
 * @author Roman.Shein
 * @since 10.09.2015.
 */
class ScalaIntroduceFieldPostfixTemplate extends PostfixTemplateWithExpressionSelector("field", "field = expr",
  new AncestorSelector(SelectorConditions.ANY_EXPR, All)) {
  override def expandForChooseExpression(expression: PsiElement, editor: Editor): Unit = {
    val range = expression.getTextRange
    editor.getSelectionModel.setSelection(range.getStartOffset, range.getEndOffset)
    new ScalaIntroduceFieldFromExpressionHandler().invoke(expression.getProject, editor, expression.getContainingFile,
      expression.getTextRange.getStartOffset, expression.getTextRange.getEndOffset)
  }
}
