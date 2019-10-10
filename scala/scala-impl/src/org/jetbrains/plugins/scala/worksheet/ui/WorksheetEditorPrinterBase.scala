package org.jetbrains.plugins.scala.worksheet.ui

import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.{Document, Editor, VisualPosition}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetDiffSplitters.SimpleWorksheetSplitter
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetEditorPrinterBase._


abstract class WorksheetEditorPrinterBase(protected val originalEditor: Editor,
                                          protected val worksheetViewer: Editor) extends WorksheetEditorPrinter {

  protected implicit val project: Project = originalEditor.getProject

  protected val originalDocument: Document = originalEditor.getDocument
  protected val viewerDocument: Document = worksheetViewer.getDocument

  protected val viewerFolding: FoldingModelEx = worksheetViewer.getFoldingModel.asInstanceOf[FoldingModelEx]

  protected lazy val foldGroup = new WorksheetFoldGroup(worksheetViewer, originalEditor, project, getWorksheetSplitter.orNull)

  private var inited = false

  override def internalError(errorMessage: String): Unit =
    invokeLater {
      inWriteAction {
        val internalErrorPrefix = "Internal error"
        val reason = if(errorMessage == null) "" else s": $errorMessage"
        val fullErrorMessage = s"$internalErrorPrefix$reason"
        val documentAlreadyContainsErrors = viewerDocument.getCharsSequence.startsWith(internalErrorPrefix)
        if(documentAlreadyContainsErrors) {
          simpleAppend("\n" + fullErrorMessage, viewerDocument)
        } else {
          simpleUpdate(fullErrorMessage, viewerDocument)
        }
      }
    }

  protected def getWorksheetSplitter: Option[SimpleWorksheetSplitter] =
    Option(worksheetViewer.getUserData(WorksheetEditorPrinterFactory.DIFF_SPLITTER_KEY))

  protected def getWorksheetViewersRation: Float =
    getWorksheetSplitter.map(_.getProportion).getOrElse(WorksheetEditorPrinterFactory.DEFAULT_WORKSHEET_VIEWERS_RATIO)

  protected def redrawViewerDiffs(): Unit = {
    getWorksheetSplitter.foreach(_.redrawDiffs())
  }


  protected def saveEvaluationResult(result: String): Unit = {
    WorksheetEditorPrinterFactory.saveWorksheetEvaluation(getScalaFile, result, getWorksheetViewersRation)
    redrawViewerDiffs()
  }

  protected def cleanFoldings(): Unit = {
    invokeLater {
      viewerFolding.runBatchFoldingOperation(() => {
        viewerFolding.clearFoldRegions()
      })

      worksheetViewer.getCaretModel.moveToVisualPosition(new VisualPosition(0, 0))
    }
  }

  protected def updateFoldings(foldings: Seq[FoldingOffsets]): Unit = startCommand() {
    val isExpanded = !scalaSettings.isWorksheetFoldCollapsedByDefault

    // TODO: clean up this mess with field namings,  see
    //  `FoldingOffsets`
    //  `group.addRegion`
    val operation: Runnable = () => {
      foldings.foreach {
        case FoldingOffsets(startLineIdx, foldEndOffset, inputLinesCount, originalEndLindIdx) =>
          val foldLineStartIdx = startLineIdx + inputLinesCount - 1
          val foldLineEndIdx = viewerDocument.getLineNumber(foldEndOffset)

          val foldStartOffset = viewerDocument.getLineStartOffset(foldLineStartIdx)

          val leftStartOffset = originalDocument.getLineEndOffset(originalEndLindIdx.min(originalDocument.getLineCount))

          val foldedLinesCount = foldLineEndIdx - foldLineStartIdx

          foldGroup.addRegion(
            viewerFolding,
            foldStartOffset, foldEndOffset,
            leftStartOffset,
            spaces = foldedLinesCount,
            leftSideLength = inputLinesCount,
            isExpanded = isExpanded
          )
      }

      WorksheetFoldGroup.save(getScalaFile, foldGroup)
    }
    viewerFolding.runBatchFoldingOperation(operation, false)
  }

  protected def isInited: Boolean = inited

  protected def init(): Unit = {
    inited = true

    foldGroup.installOn(viewerFolding)
    WorksheetEditorPrinterFactory.synch(originalEditor, worksheetViewer, getWorksheetSplitter, Some(foldGroup))

    cleanFoldings()
  }

  protected def buildNewLines(count: Int): String = StringUtil.repeatSymbol('\n', count)

  protected def commitDocument(doc: Document): Unit = {
    if (project.isDisposed) return //EA-70786
    PsiDocumentManager.getInstance(project).commitDocument(doc)
  }

  protected def simpleUpdate(text: String, document: Document): Unit = {
    document.setText(text)
    commitDocument(document)
  }

  protected def simpleAppend(text: String, document: Document): Unit =
    executeUndoTransparentAction {
      document.insertString(document.getTextLength, text)
      commitDocument(document)
    }

  protected def getOutputLimit: Int = scalaSettings.getOutputLimit

  private def scalaSettings = ScalaProjectSettings.getInstance(project)
}

object WorksheetEditorPrinterBase {

  // TODO: why one is line number, other is offset
  case class FoldingOffsets(
    outputStartLineIdx: Int,
    outputEndOffset: Int,
    inputLinesCount: Int,
    inputEndLineIdx: Int
  )
}
