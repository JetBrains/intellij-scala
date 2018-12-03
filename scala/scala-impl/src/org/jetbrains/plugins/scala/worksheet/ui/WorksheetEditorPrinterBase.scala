package org.jetbrains.plugins.scala.worksheet.ui

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.impl.FoldingModelImpl
import com.intellij.openapi.editor.{Document, Editor, VisualPosition}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
//import org.jetbrains.plugins.scala.worksheet.ui.WorksheetDiffSplitters.SimpleWorksheetSplitter

/**
  * User: Dmitry.Naydanov
  * Date: 03.02.17.
  */
abstract class WorksheetEditorPrinterBase(protected val originalEditor: Editor,
                                          protected val worksheetViewer: Editor) extends WorksheetEditorPrinter {
  protected val viewerFolding: FoldingModelImpl = worksheetViewer.getFoldingModel.asInstanceOf[FoldingModelImpl]
  protected implicit val project: Project = originalEditor.getProject
  protected val originalDocument: Document = originalEditor.getDocument
  protected val viewerDocument: Document = worksheetViewer.getDocument

  protected lazy val group = new WorksheetFoldGroup(worksheetViewer, originalEditor, project/*, getWorksheetSplitter.orNull*/)

  private var inited = false

  def getViewerEditor: Editor = worksheetViewer

  override def internalError(errorMessage: String): Unit = {
    invokeLater {
      inWriteAction {
        simpleUpdate("Internal error: " + errorMessage, viewerDocument)
      }
    }
  }
/*

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
*/
  protected def cleanFoldings(): Unit = {
    invokeLater {
      viewerFolding.runBatchFoldingOperation(() => {
        viewerFolding.clearFoldRegions()
      })

      worksheetViewer.getCaretModel.moveToVisualPosition(new VisualPosition(0, 0))
    }
  }

  /**
    *
    * @param foldings : (Start output, End output, Input lines count, End input)*
    */
  protected def updateFoldings(foldings: Seq[(Int, Int, Int, Int)]): Unit = startCommand() {
    val isExpanded = !ScalaProjectSettings.getInstance(project).isWorksheetFoldCollapsedByDefault

    viewerFolding.runBatchFoldingOperation(() => {
      foldings.foreach {
        case (start, end, limit, originalEnd) =>
          val offset = originalDocument getLineEndOffset java.lang.Math.min(originalEnd, originalDocument.getLineCount)
          val linesCount = viewerDocument.getLineNumber(end) - start - limit + 1

          group.addRegion(viewerFolding, viewerDocument.getLineStartOffset(start + limit - 1), end,
            offset, linesCount, limit, isExpanded)
      }

      WorksheetFoldGroup.save(getScalaFile, group)
    }, false)
  }

  protected def isInited: Boolean = inited

  protected def init(): Unit = {
    inited = true
/*

    val oldSync = originalEditor getUserData WorksheetEditorPrinterFactory.DIFF_SYNC_SUPPORT
    if (oldSync != null) oldSync.dispose()

    group.installOn(viewerFolding)
    WorksheetEditorPrinterFactory.synch(originalEditor, worksheetViewer, getWorksheetSplitter, Some(group))
*/

    cleanFoldings()
  }

  protected def getNewLines(count: Int): String = StringUtil.repeatSymbol('\n', count)

  protected def commitDocument(doc: Document): Unit = {
    if (project.isDisposed) return //EA-70786
    PsiDocumentManager.getInstance(project).commitDocument(doc)
  }

  protected def simpleUpdate(text: String, document: Document): Unit = {
    document.setText(text)
    commitDocument(document)
  }

  protected def simpleAppend(text: String, document: Document): Unit = {
    CommandProcessor.getInstance().runUndoTransparentAction {
      () => {
        document.insertString(document.getTextLength, text)
        commitDocument(document)
      }
    }
  }

  protected def getOutputLimit: Int = ScalaProjectSettings.getInstance(project).getOutputLimit
}
