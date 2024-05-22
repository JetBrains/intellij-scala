package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.{PostfixTemplateWithExpressionSelector, PostfixTemplatesUtils}
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector.SelectAllAncestors
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression.ScalaWithMatchSurrounder

/**
 * @see [[ScalaExhaustiveMatchPostfixTemplate]]
 */
final class ScalaMatchPostfixTemplate extends PostfixTemplateWithExpressionSelector(
  null,
  ScalaWithMatchSurrounder.getTemplateDescription,
  "expr match {...}",
  SelectAllAncestors(ScalaWithMatchSurrounder),
  null
) {

  override def expandForChooseExpression(expression: PsiElement, editor: Editor): Unit = {
    val file = expression.getContainingFile // not to be inlined!
    val project = expression.getProject

    PostfixTemplatesUtils.surround(ScalaWithMatchSurrounder, editor, expression) match {
      case null =>
      case range =>
        val styleManager = CodeStyleManager.getInstance(project)
        editor.getCaretModel.moveToOffset(range.getStartOffset)
        styleManager.adjustLineIndent(file, range)
    }
  }
}
