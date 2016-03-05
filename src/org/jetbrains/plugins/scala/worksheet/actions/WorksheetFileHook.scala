package org.jetbrains.plugins.scala
package worksheet.actions

import java.lang.ref.WeakReference
import java.util
import javax.swing._

import com.intellij.ide.scratch.{ScratchFileService, ScratchRootType}
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.{ApplicationManager, ModalityState}
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor._
import com.intellij.openapi.project.DumbService.DumbModeListener
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiDocumentManager, PsiManager}
import org.jetbrains.plugins.scala.compiler.CompilationProcess
import org.jetbrains.plugins.scala.components.StopWorksheetAction
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.interactive.WorksheetAutoRunner
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetViewerInfo
import org.jetbrains.plugins.scala.worksheet.ui.{WorksheetEditorPrinter, WorksheetFoldGroup, WorksheetUiConstructor}

/**
 * User: Dmitry Naydanov
 * Date: 1/24/14
 */
class WorksheetFileHook(private val project: Project) extends ProjectComponent {
  private var statusDisplay: Option[InteractiveStatusDisplay] = None
  
  override def disposeComponent() {}

  override def initComponent() {}

  override def projectClosed() {
    ApplicationManager.getApplication.invokeAndWait(new Runnable {
      def run() {
        WorksheetViewerInfo.invalidate()
      }
    }, ModalityState.any())
  }

  override def projectOpened() {
    project.getMessageBus.connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, WorksheetEditorListener)
    project.getMessageBus.connect(project).subscribe(DumbService.DUMB_MODE,
      new DumbModeListener {
      override def enteredDumbMode() {}

      override def exitDumbMode()  {
        val editor = FileEditorManager.getInstance(project).getSelectedTextEditor
        if (editor == null) return

        val file = PsiDocumentManager.getInstance(project) getPsiFile editor.getDocument
        if (file == null) return

        val vFile = file.getVirtualFile
        if (vFile == null) return

        WorksheetFileHook getPanel vFile foreach {
          case ref =>
            val panel = ref.get()
            if (panel != null) {
              panel.getComponents.foreach {
                case ab: ActionButton => ab.addNotify()
                case _ =>
              }
            }
        }
      }
    })
  }

  override def getComponentName: String = "Clean worksheet on editor close"

  def initTopComponent(file: VirtualFile, run: Boolean, exec: Option[CompilationProcess] = None) {
    if (project.isDisposed) return

    val myFileEditorManager = FileEditorManager.getInstance(project)
    val editors = myFileEditorManager.getAllEditors(file)

    for (editor <- editors) {
      WorksheetFileHook.getAndRemovePanel(file) foreach {
        case ref =>
          val p = ref.get()

          ApplicationManager.getApplication.invokeLater(new Runnable {
            override def run() {
              if (p != null) myFileEditorManager.removeTopComponent(editor, p)
            }
          })
      }
      
      val panel  = new WorksheetFileHook.MyPanel(file)
      val constructor = new WorksheetUiConstructor(panel, project)

      extensions.inReadAction {
        statusDisplay = constructor.initTopPanel(panel, file, run, exec)
      }

      myFileEditorManager.addTopComponent(editor, panel)
    }
  }

  def disableRun(file: VirtualFile, exec: Option[CompilationProcess]) {
    cleanAndAdd(file, exec map (new StopWorksheetAction(_)))
    statusDisplay.foreach(_.onStartCompiling())
  }

  def enableRun(file: VirtualFile, hasErrors: Boolean) {
    cleanAndAdd(file, Some(new RunWorksheetAction))
    statusDisplay.foreach(display => if (hasErrors) display.onFailedCompiling() else display.onSuccessfulCompiling())
  }

  private def cleanAndAdd(file: VirtualFile, action: Option[TopComponentDisplayable]) {
    WorksheetFileHook getPanel file foreach {
      case panelRef =>
        val panel = panelRef.get()
        if (panel != null) {
          val c = panel getComponent 0
          if (c != null) panel remove c
          action foreach (_.init(panel))
        }
    }
  }

  private object WorksheetEditorListener extends FileEditorManagerListener {
    private def doc(source: FileEditorManager, file: VirtualFile) = source getSelectedEditor file match {
      case txtEditor: TextEditor if txtEditor.getEditor != null => txtEditor.getEditor.getDocument
      case _ => null
    }
    
    private def isPluggable(file: VirtualFile): Boolean = {
      if (ScalaFileType.WORKSHEET_EXTENSION == file.getExtension) return true
      if (!file.isValid) return false
      
      PsiManager.getInstance(project).findFile(file) match {
        case _: ScalaFile if ScratchFileService.getInstance().getRootType(file).isInstanceOf[ScratchRootType] => true
        case _ => false
      }
    }

    override def selectionChanged(event: FileEditorManagerEvent) {}

    override def fileClosed(source: FileEditorManager, file: VirtualFile) {
      if (!isPluggable(file)) return
      
      val d = doc(source, file)
      if (d != null) WorksheetAutoRunner.getInstance(source.getProject) removeListener d
    }

    override def fileOpened(source: FileEditorManager, file: VirtualFile) {
      if (!isPluggable(file)) return

      WorksheetFileHook.this.initTopComponent(file, run = true)
      loadEvaluationResult(source, file)

      WorksheetAutoRunner.getInstance(source.getProject) addListener doc(source, file)
    }
    
    private def loadEvaluationResult(source: FileEditorManager, file: VirtualFile) {
      source getSelectedEditor file match {
        case txt: TextEditor => txt.getEditor match {
          case ext: EditorEx =>

            PsiDocumentManager getInstance project getPsiFile ext.getDocument match {
              case scalaFile: ScalaFile => WorksheetEditorPrinter.loadWorksheetEvaluation(scalaFile) foreach {
                case (result, ratio) if !result.isEmpty =>
                  val viewer = WorksheetEditorPrinter.createRightSideViewer(
                    ext, file, WorksheetEditorPrinter.createWorksheetEditor(ext), modelSync = true)
                  val document = viewer.getDocument

                  val splitter = WorksheetEditorPrinter.DIFF_SPLITTER_KEY.get(viewer)

                  extensions.inWriteAction {
                    document setText result
                    PsiDocumentManager.getInstance(project).commitDocument(document)

                    if (splitter != null) {
                      splitter setProportion ratio
                      WorksheetFoldGroup.load(viewer, ext, project, splitter, scalaFile)
                    }
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

  private def getPanel(file: VirtualFile): Option[WeakReference[MyPanel]] = Option(file2panel get file)

  def instance(project: Project) = project.getComponent(classOf[WorksheetFileHook])
}