package org.jetbrains.plugins.scala
package worksheet.interactive

import com.intellij.util.containers.ConcurrentWeakHashMap
import com.intellij.openapi.editor.event.{DocumentEvent, DocumentAdapter, DocumentListener}
import com.intellij.openapi.editor.Document
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiFile, PsiDocumentManager}
import org.jetbrains.plugins.scala.worksheet.actions.RunWorksheetAction
import org.jetbrains.plugins.scala.worksheet.server.WorksheetProcessManager
import com.intellij.util.Alarm
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.fileEditor.{TextEditor, FileEditorManager}
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetViewerInfo

/**
 * User: Dmitry.Naydanov
 * Date: 01.04.14.
 */
object WorksheetAutoRunner {
  private val RUN_DELAY_MS = 1400

  def getInstance(project: Project) = project.getComponent(classOf[WorksheetAutoRunner])
}

class WorksheetAutoRunner(project: Project, woof: WolfTheProblemSolver) extends ProjectComponent {
  private val listeners = new ConcurrentWeakHashMap[Document, DocumentListener]()
  private val myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project)

  override def disposeComponent() {}

  override def initComponent() {}

  override def projectClosed() {}

  override def projectOpened() {}

  override def getComponentName: String = "WorksheetAutoRunner"


  def addListener(document: Document) {
    if (listeners.get(document) == null) {
      val listener = new MyDocumentAdapter(document)

      document addDocumentListener listener
      listeners.put(document, listener)
    }
  }

  def removeListener(document: Document) {
    val listener = listeners remove document
    if (listener != null) document removeDocumentListener listener
  }

  private class MyDocumentAdapter(document: Document) extends DocumentAdapter {
    val documentManager = PsiDocumentManager getInstance project

    override def documentChanged(e: DocumentEvent) {
      val virtualFile = documentManager.getPsiFile(document).getVirtualFile
      myAlarm.cancelAllRequests()

      if (woof.hasSyntaxErrors(virtualFile) || WorksheetProcessManager.running(virtualFile)) return

      myAlarm.addRequest(new Runnable {
        override def run() {
          if (!woof.hasSyntaxErrors(virtualFile) && !WorksheetProcessManager.running(virtualFile))
            RunWorksheetAction.runCompiler(project, true)
        }
      }, WorksheetAutoRunner.RUN_DELAY_MS, true)
    }
  }
}