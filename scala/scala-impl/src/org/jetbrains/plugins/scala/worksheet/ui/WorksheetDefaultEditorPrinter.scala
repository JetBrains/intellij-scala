package org.jetbrains.plugins.scala.worksheet.ui

import java.awt.event.{ActionEvent, ActionListener}
import javax.swing.Timer

import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import scala.collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetSourceProcessor

/**
  * User: Dmitry.Naydanov
  * Date: 03.02.17.
  */
class WorksheetDefaultEditorPrinter(originalEditor1: Editor, worksheetViewer1: Editor, file1: ScalaFile) 
  extends WorksheetEditorPrinterBase(originalEditor1, worksheetViewer1) {
  private val timer = new Timer(WorksheetEditorPrinterFactory.IDLE_TIME_MLS, TimerListener)

  private val outputBuffer = new StringBuilder
  private val foldingOffsets = ArrayBuffer.apply[(Int, Int, Int, Int)]()
  private var linesCount = 0
  private var totalCount = 0
  private var insertedToOriginal = 0
  private var prefix = ""
  private var cutoffPrinted = false
  @volatile private var terminated = false
  @volatile private var buffed = 0

  originalEditor.asInstanceOf[EditorImpl].setScrollToCaret(false)
  worksheetViewer.asInstanceOf[EditorImpl].setScrollToCaret(false)


  override def getScalaFile: ScalaFile = file1

  override def scheduleWorksheetUpdate() {
    timer.start()
  }
  
  override def processLine(line: String): Boolean = {
    if (checkForTerminate(line)) return true

    if (!isInsideOutput && line.trim.length == 0) {
      outputBuffer append line
      totalCount += 1
    } else if (isResultEnd(line)) {
      WorksheetSourceProcessor extractLineInfoFrom line match {
        case Some((start, end)) =>
          if (!isInited) {
            val first = initExt()
            val diffBetweenFirst = first map (i => Math.min(i, start)) getOrElse start

            if (diffBetweenFirst > 0) prefix = getNewLines(diffBetweenFirst)
          }

          val differ = end - start + 1 - linesCount

          if (differ > 0) {
            outputBuffer append getNewLines(differ)
          } else if (0 > differ) {
            insertedToOriginal -= differ

            foldingOffsets += (
              (start + insertedToOriginal + differ,
                outputBuffer.length - outputBuffer.reverseIterator.takeWhile(_ == '\n').length,
                end - start + 1, end)
              )
          }

          buffed += linesCount
          if (buffed > WorksheetEditorPrinterFactory.BULK_COUNT) midFlush()
          clear()
        case _ =>
      }

    } else if (!cutoffPrinted) {
      linesCount += 1
      totalCount += 1

      if (linesCount > getOutputLimit) {
        outputBuffer append WorksheetEditorPrinterFactory.END_MESSAGE
        cutoffPrinted = true
      } else outputBuffer append line
    }

    false
  }

  override def internalError(errorMessage: String): Unit = {
    super.internalError(errorMessage)
    terminated = true
  }

  override def flushBuffer() {
    if (!isInited) initExt()
    if (terminated) return
    val str = getCurrentText

    if (timer.isRunning) timer.stop()

    updateWithPersistentScroll(viewerDocument, str)

    outputBuffer.clear()
    prefix = ""

    extensions.invokeLater {
      worksheetViewer.getMarkupModel.removeAllHighlighters()
    }
/*
    scala.extensions.inReadAction {
      saveEvaluationResult(str)
    }
*/
  }

  def midFlush() {
    if (terminated || buffed == 0) return

    val str = getCurrentText
    buffed = 0

    updateWithPersistentScroll(viewerDocument, str)
  }

  def getCurrentText: String = prefix + outputBuffer.toString()

  private def initExt(): Option[Int] = {
    init()

    if (getScalaFile != null) {
      @inline def checkFlag(psi: PsiElement) =
        psi != null && psi.getCopyableUserData(WorksheetSourceProcessor.WORKSHEET_PRE_CLASS_KEY) != null

      var s = getScalaFile.getFirstChild
      var f = checkFlag(s)

      while (s.isInstanceOf[PsiWhiteSpace] || f) {
        s = s.getNextSibling
        f = checkFlag(s)
      }

      if (s != null) extensions.inReadAction(Some(s.getTextRange.getStartOffset)) else None
    } else None
  }

  private def checkForTerminate(line: String): Boolean = {
    if (line.stripSuffix("\n") == WorksheetSourceProcessor.END_OUTPUT_MARKER) {
      flushBuffer()
      terminated = true
    }

    terminated
  }

  private def isResultEnd(line: String) = line startsWith WorksheetSourceProcessor.END_TOKEN_MARKER

  private def clear() {
    linesCount = 0
    cutoffPrinted = false
  }
  
  private def isInsideOutput = linesCount != 0

  private def updateWithPersistentScroll(document: Document, text: String) {
    val foldingOffsetsCopy = foldingOffsets.clone()
    foldingOffsets.clear()

    extensions.invokeLater {
      extensions.inWriteAction {
        val scroll = originalEditor.getScrollingModel.getVerticalScrollOffset
        val worksheetScroll = worksheetViewer.getScrollingModel.getVerticalScrollOffset

        simpleUpdate(text, document)

        originalEditor.getScrollingModel.scrollVertically(scroll)
        worksheetViewer.getScrollingModel.scrollHorizontally(worksheetScroll)

        updateFoldings(foldingOffsetsCopy)
      }
    }
  }

  object TimerListener extends ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = midFlush()
  }
}
