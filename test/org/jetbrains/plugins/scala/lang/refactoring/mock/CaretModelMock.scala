package org.jetbrains.plugins.scala.lang.refactoring.mock

import com.intellij.openapi.editor.{VisualPosition, Caret, LogicalPosition}
import java.util
import com.intellij.openapi.util.Segment

/**
 * Pavel Fatin
 */

class CaretModelMock(offset: Int, pos: LogicalPosition) extends CaretModelStub {
  override def getOffset: Int = offset

  override def getLogicalPosition: LogicalPosition = pos

  override def getAllCarets: util.Collection[Caret] = ???

  override def getPrimaryCaret: Caret = ???

  override def getCurrentCaret: Caret = ???

  override def supportsMultipleCarets(): Boolean = false

  override def runForEachCaret(runnable: Runnable): Unit = ???

  override def setCarets(caretPositions: util.List[LogicalPosition], selections: util.List[_ <: Segment]): Unit = ???

  override def removeSecondaryCarets(): Unit = ???

  override def removeCaret(caret: Caret): Boolean = ???

  override def addCaret(pos: VisualPosition): Caret = ???

  override def getCaretAt(pos: VisualPosition): Caret = ???
}