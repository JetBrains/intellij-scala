package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.openapi.editor.colors.{EditorColorsManager, TextAttributesKey}
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.psi.{PsiClass, PsiElement}
import org.apache.commons.text.StringEscapeUtils
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.highlighter.{DefaultHighlighter, ScalaColorsSchemeUtils}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.{ScBooleanLiteral, ScStringLiteral}
import org.jetbrains.plugins.scala.lang.psi.types.api.StdType

/**
 * @see [[com.intellij.codeInsight.documentation.DocumentationManagerProtocol]]
 * @see [[com.intellij.codeInsight.documentation.DocumentationManagerUtil]]
 * @define anonymousClassNote None if the class is anonymous.
 */
private [documentationProvider] object HtmlPsiUtils {

  //TODO: unify with org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocContentGenerator.hyperLinkToPsi
  def psiElementLinkWithCodeTag(
    fqn: String,
    label: String,
    attributesKey: Option[TextAttributesKey] = None,
  ): String = {
    psiElementLink(fqn, label, attributesKey, addCodeTag = true)
  }

  //TODO: unify with org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocContentGenerator.hyperLinkToPsi
  def psiElementLink(
    fqn: String,
    label: String,
    attributesKey: Option[TextAttributesKey] = None,
    addCodeTag: Boolean = true
  ): String = {
    val href = psiElementHref(fqn)
    val contentEscaped = StringEscapeUtils.escapeHtml4(label)
    val contentFinal = if (addCodeTag) s"<code>$contentEscaped</code>" else contentEscaped
    val link = s"""<a href="$href">$contentFinal</a>"""
    attributesKey.fold(link) { withStyledSpan(link, _) }
  }

  /**
   * @example `psi_element://scala.Option`
   */
  def psiElementHref(fqn: String): String = {
    val protocol = s"${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}"
    val fqnEscaped = StringEscapeUtils.escapeHtml4(fqn)
    s"$protocol$fqnEscaped"
  }

  def psiElement(element: PsiElement, label: Option[String] = None, escapeLabel: Boolean = true): String = {
    val text = label.getOrElse(element.getText)
    val escapedContent = if (escapeLabel) StringEscapeUtils.escapeHtml4(text) else text
    element match {
      // TODO: Re-implement after implementing soft keywords in ScalaDoc popups
      //case _ if isSoftKeyword(element) => withStyledSpan(escapedContent, DefaultHighlighter.KEYWORD)
      case _: ScStringLiteral   => withStyledSpan(escapedContent, DefaultHighlighter.STRING)
      case _: ScLiteral.Numeric => withStyledSpan(escapedContent, DefaultHighlighter.NUMBER)
      case _: ScBooleanLiteral  => withStyledSpan(escapedContent, DefaultHighlighter.KEYWORD)
      case _                    => escapedContent
    }
  }

  //TODO: defLinkHighlight is a misleading name! rename it
  def classLinkWithLabel(clazz: PsiClass,
                         label: String,
                         defLinkHighlight: Boolean,
                         addCodeTag: Boolean,
                         isAnnotation: Boolean = false,
                         qualNameToType: Map[String, StdType] = Map.empty): String = {
    val attributesKey =
      if (defLinkHighlight) None
      else if (isAnnotation) Some(DefaultHighlighter.ANNOTATION)
      else Some(ScalaColorsSchemeUtils.textAttributesKey(clazz, qualNameToType = qualNameToType))
    psiElementLink(clazz.qualifiedName, label, attributesKey = attributesKey, addCodeTag = addCodeTag)
  }

  def withStyledSpan(text: String, attributesKey: TextAttributesKey): String =
    HtmlSyntaxInfoUtil.appendStyledSpan(
      new java.lang.StringBuilder,
      EditorColorsManager.getInstance.getGlobalScheme.getAttributes(attributesKey),
      text,
      1.0f
    ).toString
}
