package org.jetbrains.plugins.scala.codeInsight.hints

import org.jetbrains.plugins.scala.codeInsight.{InlayHintsSettingsTestHelper, InlayHintsTestBase}


abstract class ScalaMethodChainInlayHintsTestBase extends InlayHintsTestBase with InlayHintsSettingsTestHelper {
  protected def doTest(text: String, settings: Setting[?]*): Unit = {
    val allSettings = showObviousTypeSetting(true) +: settings

    withSettings(allSettings) {
      doInlayTest(text)
    }
  }
}