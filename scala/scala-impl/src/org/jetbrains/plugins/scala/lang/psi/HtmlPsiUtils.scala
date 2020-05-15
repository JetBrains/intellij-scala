package org.jetbrains.plugins.scala.lang.psi

import org.apache.commons.lang.StringEscapeUtils.escapeHtml

/** see [[com.intellij.codeInsight.documentation.DocumentationManagerProtocol]] */
object HtmlPsiUtils {

  def psiElementLink(fqn: String, content: String): String = {
    val escapedContent = escape(content)
    val contentWrapped = s"""<code>$escapedContent</code>"""
    s"""<a href="psi_element://${escape(fqn)}">$contentWrapped</a>"""
  }

  def escape(text: String): String = escapeHtml(text)
}
