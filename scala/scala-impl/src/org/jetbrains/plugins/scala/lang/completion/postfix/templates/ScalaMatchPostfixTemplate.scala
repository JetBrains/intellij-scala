package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector
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

    val matchNode = ScalaWithMatchSurrounder.surroundedNode(Array(expression))

    val styleManager = CodeStyleManager.getInstance(expression.getProject)
    styleManager.reformat(matchNode.getPsi)

    ScalaWithMatchSurrounder.getSurroundSelectionRange(matchNode) match {
      case null =>
      case range =>
        editor.getCaretModel.moveToOffset(range.getStartOffset)
        styleManager.adjustLineIndent(file, range)
    }
  }
}
