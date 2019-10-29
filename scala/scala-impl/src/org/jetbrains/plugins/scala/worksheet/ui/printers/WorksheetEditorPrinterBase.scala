package org.jetbrains.plugins.scala.worksheet.ui.printers

import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetDiffSplitters.SimpleWorksheetSplitter
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetFoldGroup
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterBase.FoldingOffsets

abstract class WorksheetEditorPrinterBase(protected val originalEditor: Editor,
                                          protected val worksheetViewer: Editor)
  extends WorksheetEditorPrinter {

  protected implicit val project: Project = originalEditor.getProject

  protected val originalDocument: Document = originalEditor.getDocument
  protected val viewerDocument: Document = worksheetViewer.getDocument

  protected val viewerFolding: FoldingModelEx = worksheetViewer.getFoldingModel.asInstanceOf[FoldingModelEx]

  protected lazy val foldGroup = new WorksheetFoldGroup(worksheetViewer, originalEditor, project, getWorksheetSplitter)

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

  override def diffSplitter: Option[SimpleWorksheetSplitter] = getWorksheetSplitter

  protected def getWorksheetSplitter: Option[SimpleWorksheetSplitter] =
    Option(worksheetViewer.getUserData(WorksheetEditorPrinterFactory.DIFF_SPLITTER_KEY))

  protected def getWorksheetViewersRation: Float =
    getWorksheetSplitter.map(_.getProportion).getOrElse(WorksheetEditorPrinterFactory.DEFAULT_WORKSHEET_VIEWERS_RATIO)

  protected def redrawViewerDiffs(): Unit =
    getWorksheetSplitter.foreach(_.redrawDiffs())

  protected def saveEvaluationResult(result: String): Unit = {
    WorksheetEditorPrinterFactory.saveWorksheetEvaluation(getScalaFile, result, getWorksheetViewersRation)
    redrawViewerDiffs()
  }

  protected def cleanFoldingsLater(): Unit = invokeLater {
    cleanFoldings()
  }

  protected def cleanFoldings(): Unit = {
    foldGroup.clearRegions()
    viewerFolding.runBatchFoldingOperation { () =>
      viewerFolding.clearFoldRegions()
    }
  }

  protected def updateFoldings(foldings: Seq[FoldingOffsets], expandedIndexes: Set[Int] = Set.empty): Unit = startCommand() {
    def addRegion(fo: FoldingOffsets): Unit = {
      val FoldingOffsets(outputStartLine, outputEndOffset, inputLinesCount, inputEndLine, expanded) = fo

      val foldStartLine = outputStartLine + inputLinesCount - 1
      val foldEndLine = viewerDocument.getLineNumber(outputEndOffset)
      val foldedLinesCount = foldEndLine - foldStartLine

      val foldStartOffset = viewerDocument.getLineStartOffset(foldStartLine)
      val foldEndOffset = outputEndOffset

      val leftEndOffset = originalDocument.getLineEndOffset(inputEndLine.min(originalDocument.getLineCount))

      val isExpanded = expanded || !scalaSettings.isWorksheetFoldCollapsedByDefault

      foldGroup.addRegion(viewerFolding)(
        foldStartOffset = foldStartOffset,
        foldEndOffset = foldEndOffset,
        leftEndOffset = leftEndOffset,
        leftContentLines = inputLinesCount,
        spaces = foldedLinesCount,
        isExpanded = isExpanded
      )
    }

    viewerFolding.runBatchFoldingOperation(() => {
      foldings.foreach(addRegion)
      WorksheetFoldGroup.save(getScalaFile, foldGroup)
    }, false)
  }

  protected def isInited: Boolean = inited

  protected def init(): Unit = {
    inited = true

    foldGroup.installOn(viewerFolding)
    WorksheetEditorPrinterFactory.synch(originalEditor, worksheetViewer, getWorksheetSplitter, Some(foldGroup))

    cleanFoldingsLater()
  }

  protected def buildNewLines(count: Int): String = StringUtil.repeatSymbol('\n', count)

  protected def commitDocument(doc: Document): Unit = {
    if (project.isDisposed) return //EA-70786
    PsiDocumentManager.getInstance(project).commitDocument(doc)
  }

  protected def simpleUpdate(text: CharSequence, document: Document): Unit = {
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

  case class FoldingOffsets(
    outputStartLine: Int,
    outputEndOffset: Int,
    inputLinesCount: Int,
    inputEndLine: Int,
    isExpanded: Boolean = false
  )
}
