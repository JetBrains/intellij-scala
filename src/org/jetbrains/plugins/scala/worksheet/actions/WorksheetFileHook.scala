package org.jetbrains.plugins.scala
package worksheet.actions

import java.lang.ref.WeakReference
import java.util
import javax.swing._

import com.intellij.ide.scratch.{ScratchFileService, ScratchRootType}
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.fileEditor._
import com.intellij.openapi.project.DumbService.DumbModeListener
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiDocumentManager, PsiManager}
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.compiler.CompilationProcess
import org.jetbrains.plugins.scala.components.StopWorksheetAction
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.interactive.WorksheetAutoRunner
import org.jetbrains.plugins.scala.worksheet.ui.{WorksheetEditorPrinterFactory, WorksheetFoldGroup, WorksheetUiConstructor}

/**
 * User: Dmitry Naydanov
 * Date: 1/24/14
 */
class WorksheetFileHook(private val project: Project) extends AbstractProjectComponent(project)  {
  private var statusDisplay: Option[InteractiveStatusDisplay] = None

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
          ref =>
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

  def initWorksheetComponents(file: VirtualFile, run: Boolean, exec: Option[CompilationProcess] = None) {
    if (project.isDisposed) return

    val myFileEditorManager = FileEditorManager.getInstance(project)
    val editors = myFileEditorManager.getAllEditors(file)
    
    for (editor <- editors) {
      WorksheetFileHook.getAndRemovePanel(file) foreach {
        ref =>
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
        WorksheetFileHook.addReplAction(editor)
      }

      myFileEditorManager.addTopComponent(editor, panel)
    }
  }

  def disableRun(file: VirtualFile, exec: Option[CompilationProcess]) {
    WorksheetFileHook.removeReplAction(file, project)
    cleanAndAdd(file, exec map (new StopWorksheetAction(_)))
    statusDisplay.foreach(_.onStartCompiling())
  }

  def enableRun(file: VirtualFile, hasErrors: Boolean) {
    WorksheetFileHook.addReplAction(file, project)
    cleanAndAdd(file, Some(new RunWorksheetAction))
    statusDisplay.foreach(display => if (hasErrors) display.onFailedCompiling() else display.onSuccessfulCompiling())
  }

  private def cleanAndAdd(file: VirtualFile, action: Option[TopComponentDisplayable]) {
    WorksheetFileHook getPanel file foreach {
      panelRef =>
        val panel = panelRef.get()
        if (panel != null) {
          val c = panel getComponent 0
          if (c != null) panel remove c
          action foreach (_.init(panel))
        }
    }
  }

  private object WorksheetEditorListener extends FileEditorManagerListener {
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
      
      WorksheetFileHook.getDocumentFrom(source, file) foreach (
        d => WorksheetAutoRunner.getInstance(source.getProject) removeListener d
      )
    }

    override def fileOpened(source: FileEditorManager, file: VirtualFile) {
      if (!isPluggable(file)) return

      WorksheetFileHook.this.initWorksheetComponents(file, run = true)
      loadEvaluationResult(source, file)

      WorksheetFileHook.getDocumentFrom(source, file) foreach (WorksheetAutoRunner.getInstance(source.getProject).addListener(_))
    }
    
    private def loadEvaluationResult(source: FileEditorManager, file: VirtualFile) {
      source getSelectedEditor file match {
        case txt: TextEditor => txt.getEditor match {
          case ext: EditorEx =>

            PsiDocumentManager getInstance project getPsiFile ext.getDocument match {
              case scalaFile: ScalaFile => WorksheetEditorPrinterFactory.loadWorksheetEvaluation(scalaFile) foreach {
                case (result, ratio) if !result.isEmpty =>
                  val viewer = WorksheetEditorPrinterFactory.setupRightSideViewer(
                    ext, file, WorksheetEditorPrinterFactory.createWorksheetEditor(ext), modelSync = true)
                  val document = viewer.getDocument

                  val splitter = WorksheetEditorPrinterFactory.DIFF_SPLITTER_KEY.get(viewer)

                  extensions.inWriteAction {
                    document setText result
                    PsiDocumentManager.getInstance(project).commitDocument(document)

                    if (splitter != null) {
                      try {
                        splitter setProportion ratio
                        WorksheetFoldGroup.load(viewer, ext, project, splitter, scalaFile)
                      } catch {
                        case _: Exception => //ignored; if we are trying to load code stored in "plain" mode while in REPL mode
                      }
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
    
    override def equals(obj: Any): Boolean = obj.isInstanceOf[MyPanel]

    override def hashCode() = Integer.MAX_VALUE
  }
  
  private def getAndRemovePanel(file: VirtualFile): Option[WeakReference[MyPanel]] = Option(file2panel.remove(file))

  private def getPanel(file: VirtualFile): Option[WeakReference[MyPanel]] = Option(file2panel get file)
  
  private def addReplAction(file: VirtualFile, project: Project) {
    extensions.inReadAction {
      FileEditorManager.getInstance(project).getAllEditors(file) foreach addReplAction
    }
  }

  private def addReplAction(editor: FileEditor) {
    val c = editor.getComponent

    val oldActions = UIUtil.getClientProperty(c, AnAction.ACTIONS_KEY)
    val newActions =
      if (oldActions == null) util.Arrays.asList(WorksheetReplRunAction.ACTION_INSTANCE: AnAction) else
      if (oldActions contains WorksheetReplRunAction.ACTION_INSTANCE) oldActions else {
        oldActions.add(WorksheetReplRunAction.ACTION_INSTANCE)
        oldActions
      }

    UIUtil.putClientProperty(c, AnAction.ACTIONS_KEY, newActions)
  }
  
  private def removeReplAction(file: VirtualFile, project: Project) {
    extensions.inReadAction {
      FileEditorManager.getInstance(project).getAllEditors(file) foreach removeReplAction
    }
  }
  
  private def removeReplAction(editor: FileEditor) {
    val c = editor.getComponent
    val actions = UIUtil.getClientProperty(c, AnAction.ACTIONS_KEY)
    
    if (actions contains WorksheetReplRunAction.ACTION_INSTANCE) {
      val al = new util.ArrayList(actions)
      al remove WorksheetReplRunAction.ACTION_INSTANCE
      UIUtil.putClientProperty(c, AnAction.ACTIONS_KEY, al)
    }
  }

  def instance(project: Project): WorksheetFileHook = project.getComponent(classOf[WorksheetFileHook])
  
  def getEditorFrom(source: FileEditorManager, file: VirtualFile): Option[Editor] = source getSelectedEditor file match {
    case txtEditor: TextEditor if txtEditor.getEditor != null => Option(txtEditor.getEditor)
    case _ => None
  }
  
  def getDocumentFrom(source: FileEditorManager, file: VirtualFile): Option[Document] = getEditorFrom(source, file).map(_.getDocument)
}