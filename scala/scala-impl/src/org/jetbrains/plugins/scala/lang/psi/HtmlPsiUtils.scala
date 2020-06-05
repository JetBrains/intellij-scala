package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiClass
import org.apache.commons.lang.StringEscapeUtils
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt}

/**
 * @see [[com.intellij.codeInsight.documentation.DocumentationManagerProtocol]]
 * @see [[com.intellij.codeInsight.documentation.DocumentationManagerUtil]]
 */
object HtmlPsiUtils {

  def psiElementLink(fqn: String, label: String, escapeLabel: Boolean = true): String = {
    val href = psiElementHref(fqn)
    val escapedContent = if (escapeLabel) StringEscapeUtils.escapeHtml(label) else label
    val contentWrapped = s"""<code>$escapedContent</code>"""
    s"""<a href="$href">$contentWrapped</a>"""
  }

  def psiElementHref(fqn: String): String =
    s"psi_element://${StringEscapeUtils.escapeHtml(fqn)}"

  def classLink(clazz: PsiClass): String =
    psiElementLink(clazz.qualifiedName, clazz.name)

  def classLinkSafe(clazz: PsiClass): Option[String] =
    Option(clazz.qualifiedName).map(psiElementLink(_, clazz.name))

  def classFullLink(clazz: PsiClass): String = {
    val qualifiedName = clazz.qualifiedName
    psiElementLink(qualifiedName, qualifiedName)
  }
}
