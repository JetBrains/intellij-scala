package org.jetbrains.plugins.scala.editor.documentationProvider

import org.apache.commons.lang.StringEscapeUtils.escapeHtml

/** see [[com.intellij.codeInsight.documentation.DocumentationManagerProtocol]] */
object HtmlPsiUtils {

  def psiElementLink(fqn: String, content: String, withCodeTag: Boolean = true): String = {
    val escapedContent = escapeHtml(content)
    val contentWrapped = if (withCodeTag) s"""<code>$escapedContent</code>""" else escapedContent
    s"""<a href="psi_element://${escapeHtml(fqn)}">$contentWrapped</a>"""
  }
}
