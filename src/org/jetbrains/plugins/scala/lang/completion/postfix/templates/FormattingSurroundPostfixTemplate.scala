package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.{PostfixTemplatePsiInfo, PostfixTemplateWithExpressionSelector}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.SelectorType._
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression.ScalaExpressionSurrounder

/**
  * Created by Roman.Shein on 13.05.2016.
  */
class FormattingSurroundPostfixTemplate(val name: String, val descr: String, val psiInfo: PostfixTemplatePsiInfo,
                                        val surrounder: ScalaExpressionSurrounder) extends PostfixTemplateWithExpressionSelector(name, descr, AncestorSelector(surrounder, All)) {
  override def expandForChooseExpression(expression: PsiElement, editor: Editor): Unit = {
    val file = expression.getContainingFile
    val project = expression.getProject
    val range: TextRange = surrounder.surroundWithReformat(project, editor, Array(expression), doReformat = true)

    if (range != null) {
      editor.getCaretModel.moveToOffset(range.getStartOffset)
      CodeStyleManager.getInstance(project).adjustLineIndent(file, range)
    }
  }
}