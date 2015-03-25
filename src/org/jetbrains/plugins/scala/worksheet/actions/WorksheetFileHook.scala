package org.jetbrains.plugins.scala
package worksheet.actions

import java.awt.FlowLayout
import java.lang.ref.WeakReference
import java.util
import javax.swing.event.{ChangeEvent, ChangeListener}
import javax.swing.{JCheckBox, JPanel}

import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.{ApplicationManager, ModalityState}
import com.intellij.openapi.components.{ProjectComponent, ServiceManager}
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor._
import com.intellij.openapi.project.DumbService.DumbModeListener
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiDocumentManager, PsiManager}
import org.jetbrains.plugins.scala.compiler.CompilationProcess
import org.jetbrains.plugins.scala.components.StopWorksheetAction
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.worksheet.interactive.WorksheetAutoRunner
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetViewerInfo
import org.jetbrains.plugins.scala.worksheet.ui.{WorksheetEditorPrinter, WorksheetFoldGroup}

/**
 * User: Dmitry Naydanov
 * Date: 1/24/14
 */
class WorksheetFileHook(private val project: Project) extends ProjectComponent {
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

        WorksheetFileHook getPanel vFile map {
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
      WorksheetFileHook.getAndRemovePanel(file) map {
        case ref =>
          val p = ref.get()

          ApplicationManager.getApplication.invokeLater(new Runnable {
            override def run() {
              if (p != null) myFileEditorManager.removeTopComponent(editor, p)
            }
          })
      }
      val panel = new WorksheetFileHook.MyPanel(file)

      panel.setLayout(new FlowLayout(FlowLayout.LEFT))


      extensions.inReadAction {
        panel add createMakeProjectChb(file)
        panel add createAutorunChb(file)

        new CopyWorksheetAction().init(panel)
        new CleanWorksheetAction().init(panel)
        if (run) new RunWorksheetAction().init(panel) else exec map (new StopWorksheetAction(_).init(panel))
      }

      myFileEditorManager.addTopComponent(editor, panel)
    }
  }

  def disableRun(file: VirtualFile, exec: Option[CompilationProcess]) {
    cleanAndAdd(file, exec map (new StopWorksheetAction(_)))
  }

  def enableRun(file: VirtualFile) {
    cleanAndAdd(file, Some(new RunWorksheetAction))
  }

  private def createMakeProjectChb(file: VirtualFile) = {
    val makeProjectCb: JCheckBox = new JCheckBox("Make project",
      WorksheetCompiler.isMakeBeforeRun(PsiManager getInstance project findFile file))

    makeProjectCb addChangeListener new ChangeListener {
      override def stateChanged(e: ChangeEvent) {
        WorksheetCompiler.setMakeBeforeRun(PsiManager getInstance project findFile file, makeProjectCb.isSelected)
      }
    }

    makeProjectCb
  }

  private def createAutorunChb(file: VirtualFile) = {
    val psiFile = PsiManager getInstance project findFile file

    import org.jetbrains.plugins.scala.worksheet.interactive.WorksheetAutoRunner._
    val autorunChb = new JCheckBox("Interactive Mode",
      if (isSetEnabled(psiFile)) true else if (isSetDisabled(psiFile)) false else ScalaProjectSettings.getInstance(project).isInteractiveMode)
    autorunChb addChangeListener new ChangeListener {
      override def stateChanged(e: ChangeEvent) {
        WorksheetAutoRunner.setAutorun(psiFile, autorunChb.isSelected)
      }
    }
    autorunChb
  }

  private def cleanAndAdd(file: VirtualFile, action: Option[TopComponentAction]) {
    WorksheetFileHook getPanel file map {
      case panelRef =>
        val panel = panelRef.get()
        if (panel != null) {
          val c = panel getComponent 0
          if (c != null) panel remove c
          action map (_.init(panel))
        }
    }
  }

  private object WorksheetEditorListener extends FileEditorManagerListener {
    private def doc(source: FileEditorManager, file: VirtualFile) = source getSelectedEditor file match {
      case txtEditor: TextEditor if txtEditor.getEditor != null => txtEditor.getEditor.getDocument
      case _ => null
    }

    override def selectionChanged(event: FileEditorManagerEvent) {}

    override def fileClosed(source: FileEditorManager, file: VirtualFile) {
      if (ScalaFileType.WORKSHEET_EXTENSION == file.getExtension) {
        val d = doc(source, file)
        if (d != null) WorksheetAutoRunner.getInstance(source.getProject) removeListener d
      }
    }

    override def fileOpened(source: FileEditorManager, file: VirtualFile) {
      if (ScalaFileType.WORKSHEET_EXTENSION != file.getExtension) return

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
                  val viewer = WorksheetEditorPrinter.createRightSideViewer(ext, file, WorksheetEditorPrinter.createWorksheetEditor(ext), true)
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

  def instance(project: Project) = ServiceManager.getService(project, classOf[WorksheetFileHook])
}