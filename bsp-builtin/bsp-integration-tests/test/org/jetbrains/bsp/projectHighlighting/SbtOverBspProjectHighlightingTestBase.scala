package org.jetbrains.bsp.projectHighlighting

import org.jetbrains.plugins.scala.projectHighlighting.base.ScalaProjectHighlightingTestBase
import org.jetbrains.plugins.scala.util.NextEditCaretListenerLeakSuppression

abstract class SbtOverBspProjectHighlightingTestBase
  extends ScalaProjectHighlightingTestBase
    with SbtOverBspExternalSystemImportingTestCase {
  override def tearDown(): Unit =
    NextEditCaretListenerLeakSuppression.runSuppressing(super.tearDown())
}
