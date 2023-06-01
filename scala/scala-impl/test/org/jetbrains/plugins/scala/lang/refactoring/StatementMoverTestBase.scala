package org.jetbrains.plugins.scala
package lang.refactoring

import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.EditorTestUtil
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.base.{ScalaCodeParsing, ScalaLightCodeInsightFixtureTestCase}
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.refactoring.mock.EditorMock
import org.junit.Assert._

abstract class StatementMoverTestBase extends ScalaLightCodeInsightFixtureTestCase with ScalaCodeParsing {
  protected val | = EditorTestUtil.CARET_TAG

  private def isAvailable(code: String, direction: Direction): Boolean = {
    val offset = code.indexOf(|)
    val cleanCode = code.replace(|, "")
    val file = cleanCode.parse(version)(getProject)
    val editor = new EditorMock(cleanCode, offset)

    new ScalaStatementMover()
      .checkAvailable(editor, file, new StatementUpDownMover.MoveInfo(), direction == Down)
  }

  private def adjust(text: String): String = text
    .withNormalizedSeparator
    // add a newline at the end of the text if it's not there
    .replaceFirst("(?-m)\n?$", "\n")

  private def move(code: String, direction: Direction): Option[String] = {
    val cursors = StringUtils.countMatches(code, |)
    if (cursors == 0) fail("No cursor offset specified in the code: " + code)
    if (cursors > 1) fail("Multiple cursor offset specified in the code: " + code)

    val adjustedCode = adjust(code)

    Option.when(isAvailable(adjustedCode, direction)) {
      myFixture.configureByText(ScalaFileType.INSTANCE, adjustedCode)
      val action =
        if (direction == Down) IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION
        else IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION

      myFixture.performEditorAction(action)
      myFixture.getFile.getText
    }
  }

  private class Direction
  private case object Up extends Direction
  private case object Down extends Direction

  protected implicit class Movable(private val code: String) {
    def moveUpIsDisabled(): Unit = {
      assertEquals(None, move(code, Up))
    }

    def moveDownIsDisabled(): Unit = {
      assertEquals(None, move(code, Down))
    }

    def movedUpIs(s: String): Unit = {
      assertEquals(Some(adjust(s)), move(code, Up))
    }

    def movedDownIs(s: String): Unit = {
      assertEquals(Some(adjust(s)), move(code, Down))
    }
  }
}
