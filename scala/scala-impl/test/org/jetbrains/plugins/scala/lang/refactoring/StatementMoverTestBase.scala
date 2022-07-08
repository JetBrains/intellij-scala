package org.jetbrains.plugins.scala
package lang.refactoring

import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.refactoring.mock.EditorMock
import org.junit.Assert._

abstract class StatementMoverTestBase extends SimpleTestCase {
  private def move(code: String, direction: Direction): Option[String] = {
    val preparedCode = code.replaceAll("\r\n", "\n")

    val cursors = preparedCode .count(_ == '|')
    if(cursors == 0) fail("No cursor offset specified in the code: " + code)
    if(cursors > 1) fail("Multiple cursor offset specified in the code: " + code)

    val offset = preparedCode.indexOf("|")

    val cleanCode = preparedCode.replaceAll("\\|", "").replaceFirst("(?-m)\n?$", "\n")
    val file = cleanCode.parse
    val editor = new EditorMock(cleanCode, offset)

    val mover = new ScalaStatementMover()
    val info = new StatementUpDownMover.MoveInfo()

    val available = mover.checkAvailable(editor, file, info, direction == Down)

    available.option {
      val it = cleanCode.split('\n').toList.iterator // Workaround for SI-5972 (should be without "toList")

      val (i1, i2) = if(info.toMove.startLine < info.toMove2.startLine)
        (info.toMove, info.toMove2) else (info.toMove2, info.toMove)

      val a = it.take(i1.startLine).toList
      val source = it.take(i1.endLine - i1.startLine).toList
      val b = it.take(i2.startLine - i1.endLine).toList
      val dest = it.take(i2.endLine - i2.startLine).toList
      val c = it.toList

      (a ++ dest ++ b ++ source ++ c).mkString("\n")
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
      assertEquals(Some(s), move(code, Up))
    }

    def movedDownIs(s: String): Unit = {
      assertEquals(Some(s), move(code, Down))
    }
  }
}
