package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiClass
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.{NamedColorUtil, UIUtil}
import com.intellij.util.ui.UIUtil.FontColor
import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt}

/**
 * @see [[com.intellij.codeInsight.documentation.DocumentationManagerProtocol]]
 * @see [[com.intellij.codeInsight.documentation.DocumentationManagerUtil]]
 * @define anonymousClassNote None if the class is anonymous.
 */
object HtmlPsiUtils {

  def psiElementLink(fqn: String, label: String, escapeLabel: Boolean = true, defLinkHighlight: Boolean = true): String = {
    val href           = psiElementHref(fqn)
    val escapedContent = if (escapeLabel) StringEscapeUtils.escapeHtml(label) else label
    val style =
      if (defLinkHighlight)
        ""
      else {
        val textColor = UIUtil.getLabelFontColor(FontColor.NORMAL)
        val rgb = ColorUtil.toHex(textColor)
        s"""style="color:$rgb; text-decoration: none; background-color: none;""""
      }
    s"""<a href="$href" $style><code>$escapedContent</code></a>"""
  }

  def psiElementHref(fqn: String): String =
    s"psi_element://${StringEscapeUtils.escapeHtml(fqn)}"

  def classLink(clazz: PsiClass, defLinkHighlight: Boolean = true): String =
    psiElementLink(clazz.qualifiedName, clazz.name, defLinkHighlight = defLinkHighlight)

  /** @return link to the `clazz` psi element with a short class name. <br>$anonymousClassNote */
  def classLinkSafe(clazz: PsiClass, defLinkHighlight: Boolean = true): Option[String] =
    Option(clazz.qualifiedName).map(psiElementLink(_, clazz.name, defLinkHighlight = defLinkHighlight))

  /** @return link to the `clazz` psi element with a full qualified class name. <br>$anonymousClassNote */
  def classFullLinkSafe(clazz: PsiClass, defLinkHighlight: Boolean = true): Option[String] =
    Option(clazz.qualifiedName).map(qn => psiElementLink(qn, qn, defLinkHighlight = defLinkHighlight))
}
