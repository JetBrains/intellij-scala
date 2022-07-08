package org.jetbrains.plugins.scala.lang.refactoring.mock

import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.{Editor, LogicalPosition, SelectionModel, VisualPosition}

class SelectionModelStub(editor: Editor) extends SelectionModel {
  override def getTextAttributes: TextAttributes = null

  override def getBlockSelectionEnds: Array[Int] = null

  override def getBlockSelectionStarts: Array[Int] = null

  override def setBlockSelection(blockStart: LogicalPosition, blockEnd: LogicalPosition): Unit = {}

  override def copySelectionToClipboard(): Unit = {}

  override def selectWordAtCaret(honorCamelWordsSettings: Boolean): Unit = {}

  override def selectLineAtCaret(): Unit = {}

  override def removeSelectionListener(listener: SelectionListener): Unit = {}

  override def addSelectionListener(listener: SelectionListener): Unit = {}

  override def removeSelection(): Unit = {}

  override def setSelection(startOffset: Int, endOffset: Int): Unit = {}

  override def setSelection(startOffset: Int, endPosition: VisualPosition, endOffset: Int): Unit = {}

  override def setSelection(startPosition: VisualPosition, startOffset: Int, endPosition: VisualPosition, endOffset: Int): Unit = {}

  override def hasSelection: Boolean = false

  override def getLeadSelectionOffset: Int = 0

  override def getLeadSelectionPosition: VisualPosition = null

  override def getSelectionStartPosition: VisualPosition = null

  override def getSelectionEndPosition: VisualPosition = null

  override def getSelectedText: String = ""

  override def getSelectionEnd: Int = 0

  override def getSelectionStart: Int = 0

  override def removeSelection(allCarets: Boolean): Unit = {}

  override def hasSelection(anyCaret: Boolean): Boolean = false

  override def getSelectedText(allCarets: Boolean): String = null

  override def getEditor: Editor = editor
}