package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector.SelectAllAncestors
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaIntroduceVariableHandler

final class ScalaIntroduceVariablePostfixTemplate extends PostfixTemplateWithExpressionSelector(
  null,
  "var",
  "val name = expr",
  SelectAllAncestors(),
  null
) {

  override def expandForChooseExpression(expression: PsiElement, editor: Editor): Unit = {
    val range = expression.getTextRange
    val startOffset = range.getStartOffset
    val endOffset = range.getEndOffset

    editor.getSelectionModel.setSelection(startOffset, endOffset)
    new ScalaIntroduceVariableHandler()
      .invokeExpression(expression.getContainingFile, startOffset, endOffset)(expression.getProject, editor)
  }
}
