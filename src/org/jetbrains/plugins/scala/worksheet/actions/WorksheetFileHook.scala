package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.fileEditor.{TextEditor, FileEditorManagerEvent, FileEditorManager, FileEditorManagerListener}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.editor.ex.EditorEx
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetViewerInfo
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetEditorPrinter
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala

/**
 * User: Dmitry Naydanov
 * Date: 1/24/14
 */
class WorksheetFileHook(private val project: Project) extends ProjectComponent {
  override def disposeComponent() {}

  override def initComponent() {}

  override def projectClosed() {
    scala.extensions.invokeLater {
      WorksheetViewerInfo.invalidate()
    }
  }

  override def projectOpened() {
    project.getMessageBus.connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, WorksheetEditorListener)
  }

  override def getComponentName: String = "Clean worksheet on editor close"
  
  private object WorksheetEditorListener extends FileEditorManagerListener {
    override def selectionChanged(event: FileEditorManagerEvent) {}

    override def fileClosed(source: FileEditorManager, file: VirtualFile) {}

    override def fileOpened(source: FileEditorManager, file: VirtualFile) {
      source getSelectedEditor file match {
        case txt: TextEditor => txt.getEditor match {
          case ext: EditorEx =>

            PsiDocumentManager getInstance project getPsiFile ext.getDocument match {
              case scalaFile: ScalaFile => WorksheetEditorPrinter.loadWorksheetEvaluation(scalaFile) foreach {
                case result if !result.isEmpty =>
                  val viewer = WorksheetEditorPrinter.createWorksheetViewer(ext, file, true)
                  val document = viewer.getDocument
                  
                  extensions.inWriteAction {
                    document setText result
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                  }
                case _ => 
              }
              case _ => 
            }
          case _ =>
        }
        case _ =>
      }
    }
  }
}
