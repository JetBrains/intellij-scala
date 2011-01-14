package org.jetbrains.plugins.scala.lang.refactoring.mock

import com.intellij.openapi.editor.{VisualPosition, LogicalPosition, CaretModel}
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.event.CaretListener

/**
 * Pavel Fatin
 */

class CaretModelStub extends CaretModel {
  def getTextAttributes: TextAttributes = null

  def getVisualLineEnd: Int = 0

  def getVisualLineStart: Int = 0

  def removeCaretListener(listener: CaretListener): Unit = {}

  def addCaretListener(listener: CaretListener): Unit = {}

  def getOffset: Int = 0

  def getVisualPosition: VisualPosition = null

  def getLogicalPosition: LogicalPosition = null

  def moveToOffset(offset: Int, locateBeforeSoftWrap: Boolean): Unit = {}

  def moveToOffset(offset: Int): Unit = {}

  def moveToVisualPosition(pos: VisualPosition): Unit = {}

  def moveToLogicalPosition(pos: LogicalPosition): Unit = {}

  def moveCaretRelatively(columnShift: Int, lineShift: Int, withSelection: Boolean, blockSelection: Boolean, scrollToCaret: Boolean): Unit = {}

  def isUpToDate = false
}