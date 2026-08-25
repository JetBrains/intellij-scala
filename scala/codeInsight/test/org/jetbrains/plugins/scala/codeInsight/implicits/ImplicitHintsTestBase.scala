package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.psi.{PsiClass, PsiNamedElement}
import org.jetbrains.plugins.scala.codeInsight.InlayHintsTestBase
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.junit.Assert

trait ImplicitHintsTestBase extends InlayHintsTestBase {
  protected def doTest(text: String, expand: Boolean = false): Unit = {
    val oldEnabled = ImplicitHints.enabled
    val oldExpanded = ImplicitHints.expanded
    try {
      ImplicitHints.enabled = true
      ImplicitHints.expanded = expand
      doInlayTest(text)
    } finally {
      ImplicitHints.enabled = oldEnabled
      ImplicitHints.expanded = oldExpanded
    }
  }

  /**
   * Renders every part of every implicit hint in `text` as `part` or, when the part is navigable,
   * as `part{target}`, and asserts that the result is `expected`.
   */
  protected def doNavigationTest(text: String, expected: String): Unit = {
    val oldEnabled = ImplicitHints.enabled
    try {
      ImplicitHints.enabled = true

      val actual = inlayPartsIn(text).map { part =>
        part.navigatable.fold(part.string)(target => s"${part.string}{${labelOf(target)}}")
      }.mkString

      Assert.assertEquals(expected, actual)
    } finally {
      ImplicitHints.enabled = oldEnabled
    }
  }

  private def labelOf(target: Any): String = target match {
    case cls: PsiClass => Option(cls.qualifiedName).getOrElse(cls.name)
    case named: PsiNamedElement =>
      val owner = named.nameContext
        .asOptionOf[ScMember]
        .flatMap(member => Option(member.containingClass))
        .flatMap(cls => Option(cls.qualifiedName))
      owner.fold(named.name)(o => s"$o.${named.name}")
    case other => other.toString
  }

  /** Error tooltip messages of all implicit hint inlays in `text`, in document order. */
  protected def errorTooltips(text: String): Seq[String] = {
    val oldEnabled = ImplicitHints.enabled
    try {
      ImplicitHints.enabled = true
      inlayErrorTooltips(text.trim)
    } finally {
      ImplicitHints.enabled = oldEnabled
    }
  }
}
