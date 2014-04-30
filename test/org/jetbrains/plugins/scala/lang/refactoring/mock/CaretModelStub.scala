package org.jetbrains.plugins.scala.lang.refactoring.mock

import com.intellij.openapi.editor._
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.event.CaretListener
import java.util

/**
 * Pavel Fatin
 */

class CaretModelStub extends CaretModel {
  override def setCaretsAndSelections(caretStates: util.List[CaretState]): Unit = ???

  override def getCaretCount: Int = ???

  override def getTextAttributes: TextAttributes = null

  override def getVisualLineEnd: Int = 0

  override def getVisualLineStart: Int = 0

  override def removeCaretListener(listener: CaretListener): Unit = {}

  override def addCaretListener(listener: CaretListener): Unit = {}

  override def getOffset: Int = 0

  override def getVisualPosition: VisualPosition = null

  override def getLogicalPosition: LogicalPosition = null

  override def moveToOffset(offset: Int, locateBeforeSoftWrap: Boolean): Unit = {}

  override def moveToOffset(offset: Int): Unit = {}

  override def moveToVisualPosition(pos: VisualPosition): Unit = {}

  override def moveToLogicalPosition(pos: LogicalPosition): Unit = {}

  override def moveCaretRelatively(columnShift: Int, lineShift: Int, withSelection: Boolean, blockSelection: Boolean, scrollToCaret: Boolean): Unit = {}

  override def isUpToDate = false

  override def removeSecondaryCarets(): Unit = ???

  override def removeCaret(caret: Caret): Boolean = ???

  override def addCaret(pos: VisualPosition): Caret = ???

  override def getAllCarets: util.List[Caret] = ???

  override def getCaretAt(pos: VisualPosition): Caret = ???

  override def getPrimaryCaret: Caret = ???

  override def getCurrentCaret: Caret = ???

  override def supportsMultipleCarets(): Boolean = false

  override def runBatchCaretOperation(runnable: Runnable): Unit = ???

  override def runForEachCaret(action: CaretAction): Unit = ???

  override def getCaretsAndSelections: util.List[CaretState] = ???
}