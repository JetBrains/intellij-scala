package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.editor.colors.{EditorColorsManager, TextAttributesKey}
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.psi.{PsiClass, PsiElement}
import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.highlighter.{DefaultHighlighter, ScalaColorsSchemeUtils}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.{ScBooleanLiteral, ScStringLiteral}

/**
 * @see [[com.intellij.codeInsight.documentation.DocumentationManagerProtocol]]
 * @see [[com.intellij.codeInsight.documentation.DocumentationManagerUtil]]
 * @define anonymousClassNote None if the class is anonymous.
 */
object HtmlPsiUtils {
  def psiElement(element: PsiElement, label: Option[String] = None, escapeLabel: Boolean = true): String = {
    val text = label.getOrElse(element.getText)
    val escapedContent = if (escapeLabel) StringEscapeUtils.escapeHtml(text) else text
    element match {
      // TODO: Re-implement after implementing soft keywords in ScalaDoc popups
      //case _ if isSoftKeyword(element) => withStyledSpan(escapedContent, DefaultHighlighter.KEYWORD)
      case _: ScStringLiteral   => withStyledSpan(escapedContent, DefaultHighlighter.STRING)
      case _: ScLiteral.Numeric => withStyledSpan(escapedContent, DefaultHighlighter.NUMBER)
      case _: ScBooleanLiteral  => withStyledSpan(escapedContent, DefaultHighlighter.KEYWORD)
      case _                    => escapedContent
    }
  }

  def classLinkWithLabel(clazz: PsiClass, label: String, defLinkHighlight: Boolean): String = {
    val attributesKey =
      if (defLinkHighlight) None
      else Some(ScalaColorsSchemeUtils.textAttributesKey(clazz))
    psiElementLink(clazz.qualifiedName, label, attributesKey = attributesKey)
  }

  def psiElementLink(fqn: String, label: String, escapeLabel: Boolean = true, attributesKey: Option[TextAttributesKey] = None): String = {
    val href = psiElementHref(fqn)
    val escapedContent = if (escapeLabel) StringEscapeUtils.escapeHtml(label) else label
    val link = s"""<a href="$href"><code>$escapedContent</code></a>"""
    attributesKey.fold(link) { withStyledSpan(link, _) }
  }

  def withStyledSpan(text: String, attributesKey: TextAttributesKey): String =
    HtmlSyntaxInfoUtil.appendStyledSpan(
      new java.lang.StringBuilder,
      EditorColorsManager.getInstance.getGlobalScheme.getAttributes(attributesKey),
      text,
      1.0f
    ).toString

  def psiElementHref(fqn: String): String = s"psi_element://${StringEscapeUtils.escapeHtml(fqn)}"
}
