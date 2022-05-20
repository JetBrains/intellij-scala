package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiClass
import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt}

/**
 * @see [[com.intellij.codeInsight.documentation.DocumentationManagerProtocol]]
 * @see [[com.intellij.codeInsight.documentation.DocumentationManagerUtil]]
 * @define anonymousClassNote None if the class is anonymous.
 */
object HtmlPsiUtils {

  def psiElementLink(fqn: String, label: String, escapeLabel: Boolean = true): String = {
    val href           = psiElementHref(fqn)
    val escapedContent = if (escapeLabel) StringEscapeUtils.escapeHtml(label) else label
    val contentWrapped = s"""<code>$escapedContent</code>"""
    s"""<a href="$href">$contentWrapped</a>"""
  }

  def psiElementHref(fqn: String): String =
    s"psi_element://${StringEscapeUtils.escapeHtml(fqn)}"

  def classLink(clazz: PsiClass): String =
    psiElementLink(clazz.qualifiedName, clazz.name)

  /** @return link to the `clazz` psi element with a short class name. <br>$anonymousClassNote */
  def classLinkSafe(clazz: PsiClass): Option[String] =
    Option(clazz.qualifiedName).map(psiElementLink(_, clazz.name))

  /** @return link to the `clazz` psi element with a full qualified class name. <br>$anonymousClassNote */
  def classFullLinkSafe(clazz: PsiClass): Option[String] =
    Option(clazz.qualifiedName).map(qn => psiElementLink(qn, qn))
}
