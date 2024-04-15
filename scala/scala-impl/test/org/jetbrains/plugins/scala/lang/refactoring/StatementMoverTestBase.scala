package org.jetbrains.plugins.scala.lang.refactoring

import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.EditorTestUtil
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.ScalaFileType
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
      .checkAvailable(editor, file, new StatementUpDownMover.MoveInfo(), direction == Direction.Down)
  }

  private def doMoveAction(code0: String, direction: Direction): Unit = {
    val code = code0.withNormalizedSeparator

    val cursors = StringUtils.countMatches(code, |)
    if (cursors == 0)
      fail("No cursor offset specified in the code: " + code)
    if (cursors > 1)
      fail("Multiple cursor offset specified in the code: " + code)

    assertTrue(
      s"Move $direction is expected to be enabled in the given code",
      isAvailable(code, direction)
    )

    myFixture.configureByText(ScalaFileType.INSTANCE, code)
    val action =
      if (direction == Direction.Down) IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION
      else IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION

    myFixture.performEditorAction(action)
  }

  private class Direction
  private object Direction {
    case object Up extends Direction
    case object Down extends Direction
  }

  protected implicit class Movable(private val code: String) {
    def moveUpIsDisabled(): Unit = {
      assertFalse(
        "Move Up action is expected to be disabled in the given code",
        isAvailable(code, Direction.Up)
      )
    }

    def moveDownIsDisabled(): Unit = {
      assertFalse(
        "Move Down action is expected to be disabled in the given code",
        isAvailable(code, Direction.Down)
      )
    }

    def movedUpIs(expected: String): Unit = {
      doMoveAction(code, Direction.Up)
      myFixture.checkResult(expected)
    }

    def movedDownIs(expected: String): Unit = {
      doMoveAction(code, Direction.Down)
      myFixture.checkResult(expected)
    }
  }
}
