package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.postfix.templates.{PostfixTemplatesUtils, PostfixTemplateWithExpressionSelector}
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.SelectorType._
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.{SelectorConditions, AncestorSelector}
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
 * @author Roman.Shein
 * @since 08.09.2015.
 */
class ScalaCastPostfixTemplate extends PostfixTemplateWithExpressionSelector("cast", "expr.asInstanceOf[SomeType]",
  new AncestorSelector(SelectorConditions.ANY_EXPR, All)) {

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
      case _: ScSugarCallExpr | _: ScDoStmt | _: ScIfStmt | _: ScTryStmt | _: ScForStatement
           | _: ScWhileStmt | _: ScThrowStmt | _: ScReturnStmt => "(" + expression.getText + ")"
      case _ => expression.getText
    }), false)

    manager.startTemplate(editor, template)
  }

}
