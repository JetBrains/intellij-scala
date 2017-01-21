package org.jetbrains.plugins.scala
package base

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.openapi.actionSystem.IdeActions.{ACTION_EDITOR_BACKSPACE, ACTION_EDITOR_ENTER}
import com.intellij.testFramework.fixtures.CodeInsightTestFixture.CARET_MARKER
import com.intellij.testFramework.fixtures.{CodeInsightTestFixture, LightCodeInsightFixtureTestCase}
import org.jetbrains.plugins.scala.extensions.startCommand
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
  * User: Dmitry Naydanov
  * Date: 3/5/12
  */

abstract class ScalaLightCodeInsightFixtureTestAdapter extends LightCodeInsightFixtureTestCase with TestFixtureProvider {

  import ScalaLightCodeInsightFixtureTestAdapter.findCaretOffset

  private var libLoader: ScalaLibraryLoader = _

  override protected def setUp() {
    super.setUp()

    if (loadScalaLibrary) {
      getFixture.allowTreeAccessForAllFiles()
      libLoader = ScalaLibraryLoader.withMockJdk(getProject, getFixture.getModule, rootPath = null)
      libLoader.loadScala(libVersion)
    }
  }

  protected def libVersion: ScalaSdkVersion = TestUtils.DEFAULT_SCALA_SDK_VERSION

  protected def loadScalaLibrary = true

  protected def checkTextHasNoErrors(text: String): Unit = {
    getFixture.configureByText("dummy.scala", text)
    CodeFoldingManager.getInstance(getProject).buildInitialFoldings(getEditor)

    getFixture.testHighlighting(false, false, false, getFile.getVirtualFile)
  }

  private def performTest(text: String, expectedText: String)(testBody: () => Unit): Unit = {
    val stripTrailingSpaces = false
    val (actual, actualOffset) = findCaretOffset(text, stripTrailingSpaces)

    getFixture.configureByText("dummy.scala", actual)
    getFixture.getEditor.getCaretModel.moveToOffset(actualOffset)

    testBody()

    val (expected, _) = findCaretOffset(expectedText, stripTrailingSpaces)
    getFixture.checkResult(expected, stripTrailingSpaces)
  }

  /**
    * Checks file text and caret position after type action
    *
    * @param actual    Initial text. Must contain CARET_MARKER substring to specify caret position
    * @param expected  Reference text. May not contain CARET_MARKER (in this case caret position won't be checked)
    * @param charTyped Char typed
    */
  protected def checkGeneratedTextAfterTyping(actual: String, expected: String, charTyped: Char): Unit =
    performTest(actual, expected) { () =>
      getFixture.`type`(charTyped)
    }

  protected def checkGeneratedTextAfterBackspace(actual: String, expected: String): Unit =
    performTest(actual, expected) { () =>
      performEditorAction(ACTION_EDITOR_BACKSPACE)
    }

  protected def checkGeneratedTextAfterEnter(actual: String, expected: String): Unit =
    performTest(actual, expected) { () =>
      performEditorAction(ACTION_EDITOR_ENTER)
    }

  protected override def tearDown() {
    if (libLoader != null) {
      libLoader.clean()
    }
    libLoader = null
    super.tearDown()
  }

  override def getFixture: CodeInsightTestFixture = myFixture

  private def performEditorAction(action: String): Unit =
    startCommand(getProject, new Runnable {
      override def run(): Unit = getFixture.performEditorAction(action)
    }, "")
}

object ScalaLightCodeInsightFixtureTestAdapter {
  def normalize(text: String, stripTrailingSpaces: Boolean = true): String =
    text.stripMargin.replace("\r", "") match {
      case result if stripTrailingSpaces => result.trim
      case result => result
    }

  def findCaretOffset(text: String, stripTrailingSpaces: Boolean): (String, Int) = {
    val normalized = normalize(text, stripTrailingSpaces)
    (normalized.replace(CARET_MARKER, ""), normalized.indexOf(CARET_MARKER))
  }
}
