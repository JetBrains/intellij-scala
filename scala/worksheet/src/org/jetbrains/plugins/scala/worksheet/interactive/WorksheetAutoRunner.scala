package org.jetbrains.plugins.scala
package worksheet.interactive

import com.intellij.openapi.editor.event.{DocumentEvent, DocumentListener}
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.{PsiDocumentManager, PsiWhiteSpace}
import com.intellij.util.Alarm
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.worksheet.actions.WorksheetFileHook
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings
import org.jetbrains.plugins.scala.worksheet.{WorksheetFile, WorksheetUtils}

object WorksheetAutoRunner {

  def getInstance(project: Project): WorksheetAutoRunner = project.getService(classOf[WorksheetAutoRunner])
}

class WorksheetAutoRunner(project: Project) {

  private val listeners = ContainerUtil.createConcurrentWeakMap[Document, DocumentListener]()
  private val myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project.unloadAwareDisposable)

  private def getAutoRunDelay: Int = ScalaProjectSettings.getInstance(project).getAutoRunDelay
  
  def addListener(document: Document): Unit =
    if (listeners.get(document) == null) {
      val listener = new MyDocumentAdapter(document)

      document.addDocumentListener(listener)
      listeners.put(document, listener)
    }

  def removeListener(document: Document): Unit = {
    val listener = listeners.remove(document)
    if (listener != null) {
      document.removeDocumentListener(listener)
    }
  }
  
  def replExecuted(document: Document, offset: Int): Unit =
    listeners.get(document) match {
      case myAdapter: MyDocumentAdapter =>
        myAdapter.updateOffset(offset)
      case _ => 
    }

  private class MyDocumentAdapter(document: Document) extends DocumentListener {
    private val documentManager: PsiDocumentManager = PsiDocumentManager getInstance project
    private var lastProcessedOffset = 0

    def updateOffset(offset: Int): Unit =
      lastProcessedOffset = offset

    override def documentChanged(e: DocumentEvent): Unit = {
      if (project.isDisposed)
        return
      
      val psiFile = documentManager.getPsiFile(document) match {
        case ws: WorksheetFile => ws
        case _ =>
          return
      }
      val editor = WorksheetUtils.getSelectedTextEditor(project, psiFile.getVirtualFile) match {
        case Some(value) => value
        case None =>
          return
      }

      val offset = e.getOffset
      val isRepl = WorksheetFileSettings(psiFile).isRepl

      def needToResetLastLine: Boolean = offset < lastProcessedOffset || {
        val line1 = document.getLineNumber(lastProcessedOffset)
        val line2 = document.getLineNumber(offset)
        line1 == line2 && {
          val range = new TextRange(lastProcessedOffset, (offset + 1).min(document.getTextLength))
          val text = document.getText(range)
          val isBlank = text.forall(c => c == ';' || c.isWhitespace)
          !isBlank
        }
      }

      if (isRepl && needToResetLastLine) {
        WorksheetCache.getInstance(project).resetLastProcessedIncremental(editor)
      }

      if (WorksheetFileSettings(psiFile).isInteractive) {
        handleDocumentChangedInteractiveMode(e, editor, psiFile, offset, isRepl)
      }
    }

    private def handleDocumentChangedInteractiveMode(e: DocumentEvent, editor: Editor, psiFile: WorksheetFile, offset: Int, isRepl: Boolean): Unit = {
      val virtualFile = psiFile.getVirtualFile

      val fragment = e.getNewFragment
      val isTrashEvent = isRepl &&
        fragment.length == 0 &&
        e.getOffset + 1 >= e.getDocument.getTextLength &&
        e.getOldFragment.length == 0

      if (!isTrashEvent)
        myAlarm.cancelAllRequests()

      val isReplWrongChar = isRepl && {
        val length = fragment.length
        length == 0 || fragment.charAt(length - 1) != '\n'
      }

      def canRunWorksheet(vFile: VirtualFile): Boolean =
        !WolfTheProblemSolver.getInstance(project).hasSyntaxErrors(vFile) &&
          !WorksheetFileHook.isRunning(vFile)

      if (!canRunWorksheet(virtualFile) || isReplWrongChar)
        return

      val requestDelay = if (isRepl) getAutoRunDelay / 2 else getAutoRunDelay

      def needToRunWorksheet: Boolean = {
        if (psiFile.isValid) {
          if (isRepl) {
            psiFile.findElementAt(offset) match {
              case null => //it means caret is at the end
              case ws: PsiWhiteSpace if ws.getParent == psiFile => // continue
              case _ => return false
            }
          }
          canRunWorksheet(virtualFile)
        } else {
          false
        }
      }

      myAlarm.addRequest((() => {
        if (needToRunWorksheet) {
          RunWorksheetAction.runCompiler(editor, psiFile, auto = true)
        }
      }): Runnable, requestDelay, true)
    }
  }
}