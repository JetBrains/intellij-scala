package org.jetbrains.plugins.scala.codeInsight.implicits

import org.jetbrains.plugins.scala.codeInsight.InlayHintsTestBase

trait ImplicitHintsTestBase extends InlayHintsTestBase {
  protected def doTest(text: String): Unit = {
    val oldEnabled = ImplicitHints.enabled
    try {
      ImplicitHints.enabled = true
      doInlayTest(text)
    } finally {
      ImplicitHints.enabled = oldEnabled
    }
  }
}
