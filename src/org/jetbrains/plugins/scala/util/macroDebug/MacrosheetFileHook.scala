package org.jetbrains.plugins.scala
package util.macroDebug

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.{DocumentAdapter, DocumentEvent}
import com.intellij.openapi.fileEditor._
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache

/**
 * Created by ibogomolov on 28.05.14.
 */
class MacrosheetFileHook(private val project: Project) extends AbstractProjectComponent(project) {

  override def projectOpened() {
    project.getMessageBus.connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, MacrosheetEditorListener)
  }

  override def getComponentName: String = "Macrosheet component"

  private object MacrosheetEditorListener extends FileEditorManagerListener{
    override def fileOpened(source:FileEditorManager,file:VirtualFile) {
      if (!ScalaMacroDebuggingUtil.isEnabled || !ScalaFileType.INSTANCE.isMyFileType(file))
        return

      val document = source getSelectedEditor file match {
        case txtEditor: TextEditor if txtEditor.getEditor != null => txtEditor.getEditor.getDocument
        case _ => null
      }
      document.addDocumentListener(new MacrosheetSourceAutocopy(document))
    }

    override def selectionChanged(event:FileEditorManagerEvent) {}

    override def fileClosed(source:FileEditorManager,file:VirtualFile) {
      ScalaMacroDebuggingUtil.macrosToExpand.clear()
    }
  }

  private class MacrosheetSourceAutocopy(document: Document) extends DocumentAdapter {
    private val myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project)
    private val RUN_DELAY_MS = 1400

    override def documentChanged(e: DocumentEvent) {
      myAlarm.cancelAllRequests()
      myAlarm.addRequest(new Runnable {
        override def run() {
          val sourceEditor = FileEditorManager.getInstance(project).getSelectedTextEditor
          val macroEditor = WorksheetCache.getInstance(project).getViewer(sourceEditor)
          if (macroEditor != null && macroEditor.getDocument.getTextLength > 0) {
            ScalaMacroDebuggingUtil.expandMacros(sourceEditor.getProject)
          }
        }
      }, RUN_DELAY_MS, true)
    }
  }

}