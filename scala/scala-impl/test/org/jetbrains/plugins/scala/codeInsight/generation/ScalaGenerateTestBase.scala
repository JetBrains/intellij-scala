package org.jetbrains.plugins.scala
package codeInsight.generation

import com.intellij.lang.LanguageCodeInsightActionHandler
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions.startCommand
import org.junit.Assert._

/**
 * Nikolay.Tropin
 * 8/23/13
 */
abstract class ScalaGenerateTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  import ScalaLightCodeInsightFixtureTestAdapter._

  protected val handler: LanguageCodeInsightActionHandler

  protected def performTest(text: String, expectedText: String,
                  checkAvailability: Boolean = false, checkCaretOffset: Boolean = false): Unit = {
    val stripTrailingSpaces = true
    configureByText(text, stripTrailingSpaces)

    if (checkAvailability) {
      assertTrue("Generate action is not available", handlerIsValid)
    }

    startCommand("Generate Action Test") {
      handler.invoke(getProject, getEditor, getFile)
    }(getProject)

    val (expected, expectedOffset) = findCaretOffset(expectedText, stripTrailingSpaces)
    if (checkCaretOffset) {
      assertEquals("Wrong caret offset", expectedOffset, getEditor.getCaretModel.getOffset)
    }
    getFixture.checkResult(expected, stripTrailingSpaces)
  }

  protected def checkIsNotAvailable(text: String): Unit = {
    configureByText(text, stripTrailingSpaces = true)
    assertFalse("Generate action is available", handlerIsValid)
  }

  private def configureByText(text: String, stripTrailingSpaces: Boolean): Unit = {
    val (normalizedText, offset) = findCaretOffset(text, stripTrailingSpaces)

    getFixture.configureByText("dummy.scala", normalizedText)
    getEditor.getCaretModel.moveToOffset(offset)
  }

  private def handlerIsValid: Boolean = handler.isValidFor(getEditor, getFile)
}
