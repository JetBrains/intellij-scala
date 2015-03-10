package org.jetbrains.plugins.scala
package util.macroDebug

import com.intellij.openapi.application.{ApplicationManager, ModalityState}
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.{DocumentAdapter, DocumentEvent}
import com.intellij.openapi.fileEditor._
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetViewerInfo

/**
 * Created by ibogomolov on 28.05.14.
 */
class MacrosheetFileHook(private val project: Project) extends ProjectComponent{
  override def projectOpened() {
    project.getMessageBus.connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, MacrosheetEditorListener)
  }

  override def projectClosed() {
    ApplicationManager.getApplication.invokeAndWait(new Runnable {
      def run() {
        WorksheetViewerInfo.invalidate()
      }
    }, ModalityState.any())
  }

  override def disposeComponent() {}

  override def initComponent() {}

  override def getComponentName: String = "Macrosheet component"

  // def initActions(file: VirtualFile, run: Boolean, exec: Option[WorksheetProcess] = None) {
  //   //    scala.extensions.inReadAction {
  //   if (project.isDisposed) return

  //   val myFileEditorManager = FileEditorManager.getInstance(project)
  //   val editors = myFileEditorManager.getAllEditors(file)

  //   for (editor <- editors) {
  //     WorksheetFileHook.getAndRemovePanel(file) map {
  //       case ref =>
  //         val p = ref.get()
  //         if (p != null) myFileEditorManager.removeTopComponent(editor, p)
  //     }
  //     val panel = new WorksheetFileHook.MyPanel(file)

  //     panel.setLayout(new FlowLayout(FlowLayout.LEFT))

  //     if (run) new RunMacrosheetAction().init(panel) else exec map (new StopWorksheetAction(_).init(panel))
  //     new CleanMacrosheetAction().init(panel)

  //     myFileEditorManager.addTopComponent(editor, panel)
  //   }
  //   //    }
  // }

  private object MacrosheetEditorListener extends FileEditorManagerListener{
    override def fileOpened(source:FileEditorManager,file:VirtualFile) {
      if (!ScalaMacroDebuggingUtil.isEnabled || ScalaFileType.DEFAULT_EXTENSION != file.getExtension)
        return

      val document = source getSelectedEditor file match {
        case txtEditor: TextEditor if txtEditor.getEditor != null => txtEditor.getEditor.getDocument
        case _ => null
      }
      document.addDocumentListener(new MacrosheetSourceAutocopy(document))

      // MacrosheetFileHook.this.initActions(file,true)
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
          val sourcEditor = FileEditorManager.getInstance(project).getSelectedTextEditor
          val macroEditor = WorksheetViewerInfo.getViewer(sourcEditor)
          if (macroEditor != null && macroEditor.getDocument.getTextLength > 0) {
            ScalaMacroDebuggingUtil.expandMacros(sourcEditor.getProject)
          }
        }
      }, RUN_DELAY_MS, true)
    }
  }

}