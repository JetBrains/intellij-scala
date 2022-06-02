package org.jetbrains.plugins.scala.refactoring.rename2

import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.StringExt

/**
 * User: Alefas
 * Date: 04.10.11
 */

abstract class ScalaRenameTestBase extends ScalaFixtureTestCase {
  protected def doRenameTest(newName: String, fileText: String, resultText: String): Unit = {
    myFixture.configureByText("dummy.scala", fileText.withNormalizedSeparator.trim)
    myFixture.renameElementAtCaret(newName)
    myFixture.checkResult(resultText.withNormalizedSeparator.trim)
  }
}