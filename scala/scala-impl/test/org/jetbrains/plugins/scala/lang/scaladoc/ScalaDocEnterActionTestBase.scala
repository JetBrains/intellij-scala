package org.jetbrains.plugins.scala.lang.scaladoc

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.actions.ActionTestBase
import org.jetbrains.plugins.scala.lang.actions.ActionTestBase.MyDataContext
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.ShortCaretMarker
import org.junit.Assert._


// TODO: unify with org.jetbrains.plugins.scala.lang.actions.AbstractActionTestBase ?
abstract class ScalaDocEnterActionTestBase extends ScalaLightCodeInsightFixtureTestAdapter
  with ShortCaretMarker{

  protected def editor = getEditor
  protected def file = getFile

  override protected def setUp(): Unit = {
    super.setUp()
    val scalaSettings = getCurrentCodeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    scalaSettings.USE_SCALADOC2_FORMATTING = false // some tests have intentionally broken scaladoc formatting
  }

  protected def doTest(before: String, expectedAfter: String, stripTrailingSpaces: Boolean = true): Unit = {
    configureFromFileText(ScalaFileType.INSTANCE, before.withNormalizedSeparator)

    val handler = EditorActionManager.getInstance.getActionHandler(IdeActions.ACTION_EDITOR_ENTER)
    ActionTestBase.performAction(getProject, () => {
      handler.execute(editor, editor.getCaretModel.getCurrentCaret, new MyDataContext(file))
    })


    myFixture.checkResult(expectedAfter.withNormalizedSeparator, stripTrailingSpaces)
    //checkResultWithCaret(expectedAfter.withNormalizedSeparator, myFixture, stripTrailingSpaces)
  }

  private def checkResultWithCaret(expected: String, fixture: CodeInsightTestFixture, stripTrailingSpaces: Boolean): Unit = {
    val expectedFixed1 =
      if (stripTrailingSpaces) doStripTrailingSpaces(expected)
      else expected
    val expectedCaretPosition = expectedFixed1.indexOf(CARET)
    val expectedFixed2 = expectedCaretPosition match {
      case -1 => expectedFixed1
      case _  => expectedFixed1.replace(CARET, "")
    }
    fixture.checkResult(expectedFixed2, stripTrailingSpaces)
    if (expectedCaretPosition != -1) {
      assertEquals(
        "caret position is wrong",
        expectedCaretPosition, editor.getCaretModel.getOffset
      )
    }
  }

  private def doStripTrailingSpaces(actualText: String): String = {
    val document = EditorFactory.getInstance.createDocument(actualText)
    document.asInstanceOf[DocumentImpl].stripTrailingSpaces(getProject)
    document.getText
  }
}