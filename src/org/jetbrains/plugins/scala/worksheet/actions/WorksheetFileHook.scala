package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.components.{ServiceManager, ProjectComponent}
import com.intellij.openapi.fileEditor._
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.editor.ex.EditorEx
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetViewerInfo
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetEditorPrinter
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala
import javax.swing.JPanel
import java.awt.FlowLayout
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.scala.components.{WorksheetProcess, StopWorksheetAction}
import java.util
import java.lang.ref.WeakReference

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

  def initActions(file: VirtualFile, run: Boolean, exec: Option[WorksheetProcess] = None) {
//    scala.extensions.inReadAction {
      if (project.isDisposed) return

      val myFileEditorManager = FileEditorManager.getInstance(project)
      val editors = myFileEditorManager.getAllEditors(file)

      for (editor <- editors) {
        WorksheetFileHook.getAndRemovePanel(file) map {
          case ref => 
            val p = ref.get()
            if (p != null) myFileEditorManager.removeTopComponent(editor, p)
        }
        val panel = new WorksheetFileHook.MyPanel(file)
        
        panel.setLayout(new FlowLayout(FlowLayout.LEFT))

        if (run) new RunWorksheetAction().init(panel) else exec map (new StopWorksheetAction(_).init(panel))
        new CleanWorksheetAction().init(panel)
        new CopyWorksheetAction().init(panel)

        myFileEditorManager.addTopComponent(editor, panel)
      }
//    }
  }

  private object WorksheetEditorListener extends FileEditorManagerListener {
    override def selectionChanged(event: FileEditorManagerEvent) {}

    override def fileClosed(source: FileEditorManager, file: VirtualFile) {}

    override def fileOpened(source: FileEditorManager, file: VirtualFile) {
      if (ScalaFileType.WORKSHEET_EXTENSION != file.getExtension) return
      
      WorksheetFileHook.this.initActions(file, true)
      loadEvaluationResult(source, file)
    }
    
    private def loadEvaluationResult(source: FileEditorManager, file: VirtualFile) {
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

object WorksheetFileHook {
  private val file2panel = new util.WeakHashMap[VirtualFile, WeakReference[MyPanel]]()
  
  private class MyPanel(file: VirtualFile) extends JPanel {
    
    file2panel.put(file, new WeakReference[MyPanel](this))
    
    override def equals(obj: Any) = obj.isInstanceOf[MyPanel]

    override def hashCode() = Integer.MAX_VALUE
  }
  
  private def getAndRemovePanel(file: VirtualFile): Option[WeakReference[MyPanel]] = Option(file2panel.remove(file)) 
  
  def instance(project: Project) = ServiceManager.getService(project, classOf[WorksheetFileHook])
}