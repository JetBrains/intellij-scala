package org.jetbrains.plugins.scala.worksheet.ui.printers

import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{ThrowableExt, _}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetDiffSplitters.SimpleWorksheetSplitter
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetFoldGroup
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterBase.InputOutputFoldingInfo

abstract class WorksheetEditorPrinterBase(protected val originalEditor: Editor,
                                          protected val worksheetViewer: Editor)
  extends WorksheetEditorPrinter {

  protected implicit val project: Project = originalEditor.getProject

  protected val originalDocument: Document = originalEditor.getDocument
  protected val viewerDocument: Document = worksheetViewer.getDocument

  protected val viewerFolding: FoldingModelEx = worksheetViewer.getFoldingModel.asInstanceOf[FoldingModelEx]

  protected lazy val foldGroup = new WorksheetFoldGroup(worksheetViewer, originalEditor, project, getWorksheetSplitter)

  private var inited = false

  protected def debug(obj: Any): Unit =
    println(s"[${Thread.currentThread.getId}] $obj")

  override def internalError(ex: Throwable): Unit =
    invokeLater {
      inWriteAction {
        val fullErrorMessage = internalErrorMessage(ex)
        if (alreadyContainsInternalErrors(viewerDocument)) {
          simpleAppend(viewerDocument, "\n" + fullErrorMessage)
        } else {
          simpleUpdate(viewerDocument, fullErrorMessage)
        }
      }
    }

  protected final def internalErrorPrefix: String =
    ScalaBundle.message("worksheet.internal.error")

  protected final def alreadyContainsInternalErrors(document: Document): Boolean =
    document.getCharsSequence.startsWith(internalErrorPrefix)

  protected final def internalErrorMessage(ex: Throwable): String = {
    val stacktraceText = ex.stackTraceText
    val reason = if(stacktraceText == null) "" else s":\n$stacktraceText"
    s"$internalErrorPrefix$reason"
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

  protected final def updateFoldings(folding: InputOutputFoldingInfo): Unit =
    updateFoldings(Seq(folding))

  protected final def updateFoldings(foldings: Iterable[InputOutputFoldingInfo]): Unit = startCommand() {
    //debug(s"foldings: $foldings")

    def addRegion(fo: InputOutputFoldingInfo): Unit = {
      val InputOutputFoldingInfo(inputStartLine, inputEndLine, outputStartLine, outputEndLine, expanded) = fo

      val inputLinesCount = inputEndLine - inputStartLine + 1
      val foldStartLine = outputStartLine + inputLinesCount - 1
      val foldEndLine = outputEndLine
      val foldedLinesCount = foldEndLine - foldStartLine

      val foldStartOffset = viewerDocument.getLineStartOffset(foldStartLine)
      val foldEndOffset = viewerDocument.getLineEndOffset(foldEndLine)

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
    })
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

  protected def simpleUpdate(document: Document, text: CharSequence): Unit = {
    document.setText(text)
    commitDocument(document)
  }

  protected def simpleAppend(document: Document, text: CharSequence): Unit =
    executeUndoTransparentAction {
      val documentLength = document.getTextLength
      document.insertString(documentLength, text)
      commitDocument(document)
    }

  // TODO: not used, but should, now instead org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterFactory.BULK_COUNT is used
  protected def getOutputLimit: Int = scalaSettings.getOutputLimit

  private def scalaSettings = ScalaProjectSettings.getInstance(project)
}

private object WorksheetEditorPrinterBase {

  case class InputOutputFoldingInfo(
    inputStartLine: Int,
    inputEndLine: Int,
    outputStartLine: Int,
    outputEndLine: Int,
    var isExpanded: Boolean = false
  )
}
