package org.jetbrains.plugins.scala.worksheet.ui

import java.awt.event.{ActionEvent, ActionListener}

import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.{Document, Editor}
import javax.swing.Timer
import org.apache.commons.lang.StringUtils
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetSourceProcessor
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetEditorPrinterBase.FoldingOffsets

import scala.collection.mutable.ArrayBuffer

class WorksheetDefaultEditorPrinter(editor: Editor, viewer: Editor, file: ScalaFile)
  extends WorksheetEditorPrinterBase(editor, viewer) {

  private val timer = new Timer(WorksheetEditorPrinterFactory.IDLE_TIME_MLS, TimerListener)

  private val outputBuffer = new StringBuilder
  private val foldingOffsets = ArrayBuffer.apply[FoldingOffsets]()
  private var linesCount = 0
  private var totalCount = 0
  private var insertedToOriginal = 0
  private var contentOffsetPrefix = ""
  private var cutoffPrinted = false
  @volatile private var terminated = false
  @volatile private var buffed = 0

  originalEditor.asInstanceOf[EditorImpl].setScrollToCaret(false)
  worksheetViewer.asInstanceOf[EditorImpl].setScrollToCaret(false)

  override def getScalaFile: ScalaFile = file

  override def scheduleWorksheetUpdate(): Unit =
    timer.start()

  override def processLine(line: String): Boolean = {
    if (isTerminationLine(line)) {
      flushBuffer()
      terminated = true
      return true
    }

    if (!isInsideOutput && StringUtils.isBlank(line)) {
      outputBuffer.append(line)
      totalCount += 1
    } else if (isResultEnd(line)) {
      WorksheetSourceProcessor.extractLineInfoFrom(line) match {
        case Some((start, end)) =>
          if (!isInited) {
            init()

            val contentStartLineIdx = outputBuffer.prefixLength(_ == '\n')
            val extraLeadingBlankLines = start - contentStartLineIdx
            if (extraLeadingBlankLines > 0) {
              contentOffsetPrefix = buildNewLines(extraLeadingBlankLines)
            }
          }

          val differ = end - start + 1 - linesCount

          if (differ > 0) {
            val blankLines = buildNewLines(differ)
            outputBuffer.append(blankLines)
          } else if (differ < 0) {
            insertedToOriginal -= differ

            val outputStartLineIdx = start + insertedToOriginal + differ
            val outputEndOffset = {
              val text = currentText
              val trailingNewLines = text.reverseIterator.takeWhile(_ == '\n').length
              currentText.length - trailingNewLines
            }
            val inputLinesCount = end - start + 1
            val inputEndLineIdx = end

            foldingOffsets += FoldingOffsets(
              outputStartLineIdx,
              outputEndOffset,
              inputLinesCount,
              inputEndLineIdx
            )
          }

          buffed += linesCount
          if (buffed > WorksheetEditorPrinterFactory.BULK_COUNT) {
            midFlush()
          }
          clear()
        case _ =>
      }
    } else if (!cutoffPrinted) {
      linesCount += 1
      totalCount += 1

      if (linesCount > getOutputLimit) {
        outputBuffer.append(WorksheetEditorPrinterFactory.END_MESSAGE)
        cutoffPrinted = true
      } else {
        outputBuffer.append(line)
      }
    }

    false
  }

  override def internalError(errorMessage: String): Unit = {
    super.internalError(errorMessage)
    terminated = true
  }

  override def flushBuffer(): Unit = {
    if (!isInited) init()
    if (terminated) return
    val text = currentText

    if (timer.isRunning) timer.stop()

    updateWithPersistentScroll(viewerDocument, text)

    outputBuffer.clear()
    contentOffsetPrefix = ""

    invokeLater {
      worksheetViewer.getMarkupModel.removeAllHighlighters()
    }
  }

  private def midFlush(): Unit = {
    if (terminated || buffed == 0) return

    val text = currentText
    buffed = 0

    updateWithPersistentScroll(viewerDocument, text)
  }

  private def currentText: String = contentOffsetPrefix + outputBuffer.toString()

  private def isTerminationLine(line: String): Boolean =
    line.stripSuffix("\n") == WorksheetSourceProcessor.END_OUTPUT_MARKER

  private def isResultEnd(line: String): Boolean =
    line.startsWith(WorksheetSourceProcessor.END_TOKEN_MARKER)

  private def clear(): Unit = {
    linesCount = 0
    cutoffPrinted = false
  }

  private def isInsideOutput: Boolean = linesCount > 0

  private def updateWithPersistentScroll(document: Document, text: String): Unit = {
    val foldingOffsetsCopy = foldingOffsets.clone()
    foldingOffsets.clear()

    invokeLater {
      inWriteAction {
        val scroll = originalEditor.getScrollingModel.getVerticalScrollOffset
        val worksheetScroll = worksheetViewer.getScrollingModel.getVerticalScrollOffset

        simpleUpdate(text, document)

        originalEditor.getScrollingModel.scrollVertically(scroll)
        worksheetViewer.getScrollingModel.scrollHorizontally(worksheetScroll)

        updateFoldings(foldingOffsetsCopy)
      }
    }
  }

  private object TimerListener extends ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = midFlush()
  }
}
