package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.editor.colors.{EditorColorsManager, TextAttributesKey}
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.psi.PsiClass
import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
/**
 * @see [[com.intellij.codeInsight.documentation.DocumentationManagerProtocol]]
 * @see [[com.intellij.codeInsight.documentation.DocumentationManagerUtil]]
 * @define anonymousClassNote None if the class is anonymous.
 */
object HtmlPsiUtils {

  def psiElementLink(fqn: String, label: String, escapeLabel: Boolean = true, attributesKey: Option[TextAttributesKey] = None): String = {
    val href           = psiElementHref(fqn)
    val escapedContent = if (escapeLabel) StringEscapeUtils.escapeHtml(label) else label
    val link           = s"""<a href="$href"><code>$escapedContent</code></a>"""
    attributesKey.fold(link) { key =>
      HtmlSyntaxInfoUtil.appendStyledSpan(
        new java.lang.StringBuilder,
        EditorColorsManager.getInstance.getGlobalScheme.getAttributes(key),
        link,
        1.0f
      ).toString
    }
  }

  def psiElementHref(fqn: String): String =
    s"psi_element://${StringEscapeUtils.escapeHtml(fqn)}"

  def classLink(clazz: PsiClass, defLinkHighlight: Boolean = true): String =
    classLinkWithLabel(clazz, clazz.name, defLinkHighlight)

  /** @return link to the `clazz` psi element with a short class name. <br>$anonymousClassNote */
  def classLinkSafe(clazz: PsiClass, defLinkHighlight: Boolean = true): Option[String] =
    Option(clazz.qualifiedName).map(_ => classLinkWithLabel(clazz, clazz.name, defLinkHighlight))

  /** @return link to the `clazz` psi element with a full qualified class name. <br>$anonymousClassNote */
  def classFullLinkSafe(clazz: PsiClass, defLinkHighlight: Boolean = true): Option[String] =
    Option(clazz.qualifiedName).map(qn => classLinkWithLabel(clazz, qn, defLinkHighlight))

  private def classLinkWithLabel(clazz: PsiClass, label: String, defLinkHighlight: Boolean): String = {
    val attributesKey =
      if (defLinkHighlight)
        None
      else
        Some(clazz match {
          case x: ScClass if x.getModifierList.isAbstract => DefaultHighlighter.ABSTRACT_CLASS
          case _: ScTypeParam                             => DefaultHighlighter.TYPEPARAM
          case _: ScClass                                 => DefaultHighlighter.CLASS
          case _: ScObject                                => DefaultHighlighter.OBJECT
          case _: ScTrait                                 => DefaultHighlighter.TRAIT
          case x: PsiClass if x.isInterface               => DefaultHighlighter.TRAIT
          case x: PsiClass if x.getModifierList != null && x.getModifierList.hasModifierProperty("abstract") =>
            DefaultHighlighter.ABSTRACT_CLASS
          case _                                          => DefaultHighlighter.CLASS
        })
    psiElementLink(clazz.qualifiedName, label, attributesKey = attributesKey)
  }
}
