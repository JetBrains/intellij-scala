package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.postfix.templates.{PostfixTemplateWithExpressionSelector, PostfixTemplatesUtils}
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector.SelectAllAncestors
import org.jetbrains.plugins.scala.lang.psi.api.expr._

final class ScalaCastPostfixTemplate extends PostfixTemplateWithExpressionSelector(
  null,
  "cast",
  "expr.asInstanceOf[SomeType]",
  SelectAllAncestors(),
  null
) {

  def getTemplateString(expression: PsiElement): String = "$expr$.asInstanceOf[$END$]"

  override def expandForChooseExpression(expression: PsiElement, editor: Editor): Unit = {
    val document: Document = editor.getDocument
    document.deleteString(expression.getTextRange.getStartOffset, expression.getTextRange.getEndOffset)
    val manager: TemplateManager = TemplateManager.getInstance(expression.getProject)

    val templateString = getTemplateString(expression)
    if (templateString == null) {
      PostfixTemplatesUtils.showErrorHint(expression.getProject, editor)
      return
    }


    val template = manager.createTemplate("", "", templateString)
    template.setToReformat(true)

    template.addVariable("expr", new TextExpression(expression match {
      case _: ScSugarCallExpr | _: ScDo | _: ScIf | _: ScTry | _: ScFor
           | _: ScWhile | _: ScThrow | _: ScReturn => "(" + expression.getText + ")"
      case _ => expression.getText
    }), false)

    manager.startTemplate(editor, template)
  }

}
