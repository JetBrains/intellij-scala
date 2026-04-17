package org.jetbrains.plugins.scala.projectHighlighting.base

import org.jetbrains.plugins.scala.util.NextEditCaretListenerLeakSuppression
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike

abstract class SbtProjectHighlightingTestBase
  extends ScalaProjectHighlightingTestBase
    with SbtExternalSystemImportingTestLike {

  override def tearDown(): Unit =
    NextEditCaretListenerLeakSuppression.runSuppressing(super.tearDown())
}
