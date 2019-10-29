package org.jetbrains.plugins.scala.worksheet.ui.printers

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.util.text.StringUtil
import javax.swing.Timer
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.macroAnnotations.Measure
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetSourceProcessor
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterBase.FoldingOffsets
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterPlain._

import scala.collection.mutable.ArrayBuffer

final class WorksheetEditorPrinterPlain private[printers](editor: Editor, viewer: Editor, file: ScalaFile)
  extends WorksheetEditorPrinterBase(editor, viewer) {

  // used to flush collected output if there is some long process generating running
  private val flushTimer = new Timer(WorksheetEditorPrinterFactory.IDLE_TIME_MLS, _ => midFlush())

  private val evaluatedChunks = ArrayBuffer[EvaluationChunk]()

  private val currentOutputBuffer = StringBuilder.newBuilder
  private var currentOutputNewLinesCount = 0
  private var currentResultStartLine: Option[String] = None

  private var cutoffPrinted = false
  @volatile private var terminated = false
  @volatile private var buffed = 0

  originalEditor.asInstanceOf[EditorImpl].setScrollToCaret(false)
  worksheetViewer.asInstanceOf[EditorImpl].setScrollToCaret(false)

  override def getScalaFile: ScalaFile = file

  override def scheduleWorksheetUpdate(): Unit = flushTimer.start()

  override def processLine(line: String): Boolean = {
    if (isTerminationLine(line)) {
      flushBuffer()
      terminated = true
      return true
    }

    if (isResultStart(line)) {
      currentResultStartLine = Some(line)
    } else if (isResultEnd(line)) {
      if (!isInited) init()

      WorksheetSourceProcessor.extractLineInfoFromEnd(line) match {
        case Some((inputStartLine, inputEndLine)) =>
          val output = currentOutputBuffer.mkString
          val chunk  = EvaluationChunk(inputStartLine, inputEndLine, output)
          evaluatedChunks += chunk
        case _ =>
      }

      currentOutputBuffer.clear()
      currentOutputNewLinesCount = 0
      cutoffPrinted = false
    } else {
      if (currentOutputNewLinesCount < WorksheetEditorPrinterFactory.BULK_COUNT) {
        currentOutputBuffer.append(line)
      } else if (!cutoffPrinted) {
        currentOutputBuffer.append(WorksheetEditorPrinterFactory.END_MESSAGE)
        cutoffPrinted = true
      }

      currentOutputNewLinesCount += 1
      buffed += 1
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

    if (flushTimer.isRunning) {
      flushTimer.stop()
    }

    val lastChunk = buildIncompleteLastChunkOpt
    val expandedIndexes = (foldGroup.expandedRegionsIndexes.toIterator ++ lastChunk.map(_ => evaluatedChunks.size)).toSet
    val (text, foldings) = renderText(evaluatedChunks ++ lastChunk, expandedIndexes)
    updateWithPersistentScroll(viewerDocument, text, foldings)

    invokeLater {
      worksheetViewer.getMarkupModel.removeAllHighlighters()
    }
  }

  private def buildIncompleteLastChunkOpt: Option[EvaluationChunk] = {
    if (StringUtils.isNotBlank(currentOutputBuffer)) {
      val (inputStartLine, inputEndLine) =
        currentResultStartLine.flatMap(WorksheetSourceProcessor.extractLineInfoFromStart).getOrElse(0, 0)
      val output = currentOutputBuffer.mkString
      Some(EvaluationChunk(inputStartLine, inputEndLine, output))
    } else {
      None
    }
  }

  // currently we re-render text on each mid-flush (~once per 1 second for long processes),
  // for now we are ok with this cause `renderText` proved to be quite a lightweight operation
  private def midFlush(): Unit = {
    if (terminated || buffed  == 0) return

    buffed = 0

    val expandedIndexes = foldGroup.expandedRegionsIndexes.toSet
    val (text, foldings) = renderText(evaluatedChunks, expandedIndexes)
    updateWithPersistentScroll(viewerDocument, text, foldings)
  }

  private def isTerminationLine(line: String): Boolean =
    line.stripSuffix("\n") == WorksheetSourceProcessor.END_OUTPUT_MARKER

  private def isResultStart(line: String): Boolean =
    line.startsWith(WorksheetSourceProcessor.START_TOKEN_MARKER)

  private def isResultEnd(line: String): Boolean =
    line.startsWith(WorksheetSourceProcessor.END_TOKEN_MARKER)

  private def updateWithPersistentScroll(document: Document, text: CharSequence, foldings: Seq[FoldingOffsets]): Unit =
    invokeLater {
      inWriteAction {
        val scroll          = originalEditor.getScrollingModel.getVerticalScrollOffset
        val worksheetScroll = worksheetViewer.getScrollingModel.getVerticalScrollOffset

        simpleUpdate(text, document)

        originalEditor.getScrollingModel.scrollVertically(scroll)
        worksheetViewer.getScrollingModel.scrollHorizontally(worksheetScroll)

        // NOTE: if a folding already exists in a folding group it will note be duplicated
        // see FoldingModelImpl.createFoldRegion
        updateFoldings(foldings)
        foldGroup.initMappings()
      }
    }
}

object WorksheetEditorPrinterPlain {

  private val Log = Logger.getInstance(classOf[WorksheetEditorPrinterPlain])

  /**
   * Represents evaluated expression which is located on `inputStartLine..inputEndLine` lines in the left editor
   * and which evaluation output equals to `outputText`
   */
  private case class EvaluationChunk(inputStartLine: Int,
                                     inputEndLine: Int,
                                     outputText: String) {
    // REMEMBER: output always goes with trailing new line
    def outputLinesCount: Int = StringUtil.countNewLines(outputText)
  }

  /**
   * @return grouped of sequential chunks, each group represent chunks that go on a single line
   *         meaning that left sibling chunk end line equals to right sibling chunk start line
   *         (this can happen when expressions are separated with comma)
   *
   * @example in format (lineStart, lineEnd): <br>
   *          input: Seq((1, 1), (1, 1), (1, 2), (2, 2), (3, 4), (4, 5)) <br>
   *          output: Seq(Seq((1, 1), (1, 1), (1, 2), (2, 2)), Seq((3, 4), (4, 5))) <br>
   */
  private def groupChunks(chunks: Seq[EvaluationChunk]): Seq[Seq[EvaluationChunk]] = if (chunks.isEmpty) Seq() else {
    val result = ArrayBuffer(ArrayBuffer(chunks.head))

    chunks.sliding(2).foreach {
      case Seq(prev, curr) =>
        assert(prev.inputEndLine <= curr.inputStartLine, "chunks should be ordered")

        if (prev.inputEndLine == curr.inputStartLine) {
          result.last += curr
        } else {
          result += ArrayBuffer(curr)
        }
      case _ => // only one chunk is present for now
    }

    result
  }


  @Measure
  private def renderText(chunks: Seq[EvaluationChunk], expandedIndexes: Set[Int]): (CharSequence, Seq[FoldingOffsets]) = {
    val resultText = StringBuilder.newBuilder
    val resultFoldings = ArrayBuffer.empty[FoldingOffsets]

    val chunksGrouped: Seq[Seq[EvaluationChunk]] = WorksheetEditorPrinterPlain.groupChunks(chunks)
    var foldedLines                              = 0

    for { (group, groupIdx) <- chunksGrouped.iterator.zipWithIndex } {
      val inputStartLine   = group.head.inputStartLine
      val inputEndLine     = group.last.inputEndLine
      val inputLinesCount  = inputEndLine - inputStartLine + 1
      val outputTextLength = group.map(_.outputText.length).sum
      val outputLinesCount = group.map(_.outputLinesCount).sum

      val totalOutputLength            = resultText.length
      val totalOutputLinesCount        = StringUtil.countNewLines(resultText)
      val totalOutputVisibleLinesCount = totalOutputLinesCount - foldedLines

      // align visible output line in the right editor with current input line from the left editor
      val leadingNewLinesCount = {
        val diff = inputStartLine - totalOutputVisibleLinesCount
        if (diff < 0){
          // expecting visible lines to be folded with the last input end line, thus less then current input start line
          // NOTE: be careful not to log chunk text itself
          val chunksDump = chunks.map { case c@EvaluationChunk(s, e, t) => (s, e, t.length, c.outputLinesCount) }
          val message = s"leadingNewLinesCount is expected to be non-negative but got: $diff, chunks: $chunksDump"
          Log.warn(message)
        }
        diff.max(0)
      }
      if (leadingNewLinesCount > 0) {
        resultText.append("\n" * leadingNewLinesCount)
      }

      group.foreach { chunk =>
        resultText.append(chunk.outputText)
      }

      val diffLocal = outputLinesCount - inputLinesCount
      if (diffLocal > 0) {
        // current output is longer than input, need to fold some output lines to align with input start/end lines
        val outputStartLine = totalOutputLinesCount + leadingNewLinesCount
        val outputEndOffset = totalOutputLength + leadingNewLinesCount + outputTextLength - 1
        val folding = FoldingOffsets(
          outputStartLine,
          outputEndOffset,
          inputLinesCount,
          inputEndLine,
          isExpanded = expandedIndexes.contains(groupIdx)
        )

        foldedLines += diffLocal
        resultFoldings += folding
      } else if (diffLocal < 0) {
        // current input is longer than output need to add extra trailing spaces after the output
        // to align input end with output last line
        val trailingNewLinesCount = -diffLocal //+ 1
        resultText.append("\n" * trailingNewLinesCount)
      } else {
        // do nothing, input and output lines are already aligned
      }
    }

    (resultText, resultFoldings)
  }
}