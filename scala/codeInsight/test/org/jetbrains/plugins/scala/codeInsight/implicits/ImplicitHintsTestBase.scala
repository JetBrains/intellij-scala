package org.jetbrains.plugins.scala.codeInsight.implicits

import org.jetbrains.plugins.scala.codeInsight.InlayHintsTestBase

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
