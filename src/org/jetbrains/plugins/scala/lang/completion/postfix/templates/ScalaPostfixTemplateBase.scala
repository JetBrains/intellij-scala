package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Roman.Shein
 * @since 10.09.2015.
 */
abstract class ScalaPostfixTemplateBase(val name: String, val example: String) extends PostfixTemplate(name, example) {
  override def isApplicable(context: PsiElement, copyDocument: Document, newOffset: Int): Boolean = {
    ScalaPsiUtil.getParentOfType(context, classOf[ScExpression]) match {
      case expr: ScExpression => expr.getTextRange.getEndOffset == newOffset
      case _ => false
    }
  }
}
