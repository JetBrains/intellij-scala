package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.{StringBasedPostfixTemplate, PostfixTemplateExpressionSelectorBase}

/**
  * @author Roman.Shein
  * @since 14.09.2015.
  */
abstract class ScalaStringBasedPostfixTemplate(val name: String, val example: String,
                                               val selector: PostfixTemplateExpressionSelectorBase) extends StringBasedPostfixTemplate(name, example, selector) {
  override def shouldRemoveParent = false
}
