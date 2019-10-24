package org.jetbrains.plugins.scala
package worksheet
package actions

import java.lang.ref.WeakReference
import java.util

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.fileEditor._
import com.intellij.openapi.project.DumbService.DumbModeListener
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiDocumentManager, PsiManager}
import com.intellij.util.ui.UIUtil
import javax.swing._
import org.jetbrains.plugins.scala.compiler.CompilationProcess
import org.jetbrains.plugins.scala.extensions.{ReferenceExt, invokeLater}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.UserDataKeys
import org.jetbrains.plugins.scala.worksheet.actions.topmenu._
import org.jetbrains.plugins.scala.worksheet.actions.repl._
import org.jetbrains.plugins.scala.worksheet.interactive.WorksheetAutoRunner
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetCommonSettings
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterFactory
import org.jetbrains.plugins.scala.worksheet.ui.{WorksheetFoldGroup, WorksheetUiConstructor}

class WorksheetFileHook(private val project: Project) extends ProjectComponent  {

  private var statusDisplay: Option[InteractiveStatusDisplay] = None

  override def getComponentName: String = "Clean worksheet on editor close"

  override def projectOpened(): Unit = {
    project.getMessageBus.connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, WorksheetEditorListener)
    project.getMessageBus.connect(project).subscribe(DumbService.DUMB_MODE, new DumbModeListener {
      override def enteredDumbMode(): Unit = {}
      override def exitDumbMode(): Unit = initializeButtons()
    })
  }

  private def initializeButtons(): Unit =
    for {
      editor   <- Option(FileEditorManager.getInstance(project).getSelectedTextEditor)
      file     <- Option(PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument))
      vFile    <- Option(file.getVirtualFile)
      panelRef <- WorksheetFileHook.getPanel(vFile)
      panel    <- panelRef.getOpt
    } notifyButtons(panel)

  private def notifyButtons(panel: WorksheetFileHook.MyPanel): Unit =
    panel.getComponents.foreach {
      case button: ActionButton =>
        button.addNotify()
      case _ =>
    }

  private def initWorksheetComponents(file: VirtualFile, run: Boolean, exec: Option[CompilationProcess] = None): Unit = {
    if (project.isDisposed) return
    if (ApplicationManager.getApplication.isUnitTestMode) return

    val myFileEditorManager = FileEditorManager.getInstance(project)
    val editors = myFileEditorManager.getAllEditors(file)

    for (editor <- editors) {
      for {
        ref <- WorksheetFileHook.getAndRemovePanel(file)
        panel <- ref.getOpt
      } invokeLater {
        myFileEditorManager.removeTopComponent(editor, panel)
      }

      val panel = new WorksheetFileHook.MyPanel(file)
      val constructor = new WorksheetUiConstructor(panel, project)

      inReadAction {
        statusDisplay = Some(constructor.initTopPanel(panel, file, run, exec))
        WorksheetFileHook.plugWorksheetActions(editor)
      }

      myFileEditorManager.addTopComponent(editor, panel)
    }
  }

  def disableRun(file: VirtualFile, exec: Option[CompilationProcess]): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode) return

    WorksheetFileHook.unplugWorksheetActions(file, project)
    cleanAndAdd(file, exec.map(new StopWorksheetAction(_)))
    statusDisplay.foreach(_.onStartCompiling())
  }

  def enableRun(file: VirtualFile, hasErrors: Boolean): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode) return

    WorksheetFileHook.plugWorksheetActions(file, project)
    cleanAndAdd(file, Some(new RunWorksheetAction))
    statusDisplay.foreach { display =>
      if (hasErrors) display.onFailedCompiling()
      else display.onSuccessfulCompiling()
    }
  }

  private def cleanAndAdd(file: VirtualFile, action: Option[TopComponentDisplayable]): Unit =
    for {
      ref <- WorksheetFileHook.getPanel(file)
      panel <- ref.getOpt
    } {
      val c = panel.getComponent(0)
      if (c != null) panel.remove(c)
      action.foreach(_.init(panel))
    }

  private object WorksheetEditorListener extends FileEditorManagerListener {

    private def isPluggable(file: VirtualFile): Boolean = file.isValid &&
      WorksheetFileType.isWorksheetFile(file)(project)

    override def selectionChanged(event: FileEditorManagerEvent): Unit = {}

    override def fileClosed(source: FileEditorManager, file: VirtualFile): Unit = {
      if (!isPluggable(file)) return

      val document = WorksheetFileHook.getDocumentFrom(source.getProject, file)
      document.foreach(WorksheetAutoRunner.getInstance(source.getProject).removeListener(_))
    }

    override def fileOpened(source: FileEditorManager, file: VirtualFile): Unit = {
      if (!isPluggable(file)) return

      WorksheetFileHook.this.initWorksheetComponents(file, run = true)
      loadEvaluationResult(source, file)
      val document = WorksheetFileHook.getDocumentFrom(source.getProject, file)
      document.foreach(WorksheetAutoRunner.getInstance(source.getProject).addListener(_))
    }

    private def ensureWorksheetModuleIsSet(file: ScalaFile): Unit = {
      val module = WorksheetCommonSettings(file).getModuleFor
      file.getOrUpdateUserData(UserDataKeys.SCALA_ATTACHED_MODULE, module)
    }

    private def loadEvaluationResult(source: FileEditorManager, vFile: VirtualFile): Unit =
      for {
        editor    <- editorWithFile(source, vFile)
        manager   = PsiDocumentManager.getInstance(project)
        scalaFile <- Option(manager.getPsiFile(editor.getDocument)).collect { case f: ScalaFile => f }
      } loadEvaluationResult(scalaFile, vFile, editor)

    private def editorWithFile(source: FileEditorManager, file: VirtualFile): Option[EditorEx] =
      Option(source.getSelectedEditor(file))
          .collect { case te: TextEditor => te.getEditor }
          .collect { case e: EditorEx => e }

    private def loadEvaluationResult(scalaFile: ScalaFile, vFile: VirtualFile, editor: EditorEx): Unit = {
      ensureWorksheetModuleIsSet(scalaFile)

      val evaluationResultOpt = WorksheetEditorPrinterFactory.loadWorksheetEvaluation(scalaFile)
      evaluationResultOpt.foreach {
        case (result, ratio) if !result.isEmpty =>
          val viewer = WorksheetEditorPrinterFactory.createViewer(editor, vFile)
          val document = viewer.getDocument

          val splitter = WorksheetEditorPrinterFactory.DIFF_SPLITTER_KEY.get(viewer)

          inWriteAction {
            document.setText(result)
            PsiDocumentManager.getInstance(project).commitDocument(document)

            if (splitter != null) {
              try {
                splitter.setProportion(ratio)
                val group = WorksheetFoldGroup.load(viewer, editor, project, splitter, scalaFile)
                WorksheetEditorPrinterFactory.synch(editor, viewer, Some(splitter), Some(group))
              } catch {
                case _: Exception => //ignored; if we are trying to load code stored in "plain" mode while in REPL mode
              }
            }
          }
        case _ =>
      }
    }
  }
}

object WorksheetFileHook {

  private val WORKSHEET_HK_ACTIONS: Array[AnAction] = Array(WorksheetReplRunAction.ACTION_INSTANCE)
  private val file2panel = new util.WeakHashMap[VirtualFile, WeakReference[MyPanel]]()

  private class MyPanel(file: VirtualFile) extends JPanel {

    file2panel.put(file, new WeakReference[MyPanel](this))

    override def equals(obj: Any): Boolean = obj.isInstanceOf[MyPanel]

    override def hashCode(): Int = Integer.MAX_VALUE
  }

  private def getAndRemovePanel(file: VirtualFile): Option[WeakReference[MyPanel]] = Option(file2panel.remove(file))

  private def getPanel(file: VirtualFile): Option[WeakReference[MyPanel]] = Option(file2panel.get(file))

  private def plugWorksheetActions(file: VirtualFile, project: Project): Unit =
    inReadAction {
      val editors = FileEditorManager.getInstance(project).getAllEditors(file)
      editors.foreach(plugWorksheetActions)
    }

  private def plugWorksheetActions(editor: FileEditor): Unit =
    patchComponentActions(editor) { oldActions =>
      if (oldActions == null) {
        util.Arrays.asList(WORKSHEET_HK_ACTIONS: _*)
      } else {
        val newActions = new util.ArrayList(oldActions)
        WORKSHEET_HK_ACTIONS.foreach { action =>
          if (!newActions.contains(action))
            newActions.add(action)
        }
        newActions
      }
    }

  private def unplugWorksheetActions(file: VirtualFile, project: Project): Unit =
    inReadAction {
      val editors = FileEditorManager.getInstance(project).getAllEditors(file)
      editors.foreach(unplugWorksheetActions)
    }

  private def unplugWorksheetActions(editor: FileEditor): Unit =
    patchComponentActions(editor) { oldActions =>
      if (oldActions == null) null else {
        val newActions = new util.ArrayList(oldActions)
        WORKSHEET_HK_ACTIONS.foreach(newActions.remove)
        newActions
      }
    }

  private def patchComponentActions(editor: FileEditor)(patcher: util.List[AnAction] => util.List[AnAction]) {
    val c = editor.getComponent
    val patchedActions = patcher(UIUtil.getClientProperty(c, AnAction.ACTIONS_KEY))
    if (patchedActions != null) {
      UIUtil.putClientProperty(c, AnAction.ACTIONS_KEY, patchedActions)
    }
  }

  def instance(project: Project): WorksheetFileHook = project.getComponent(classOf[WorksheetFileHook])

  def handleEditor(source: FileEditorManager, file: VirtualFile)(callback: Editor => Unit): Unit =
    invokeLater {
      source.getSelectedEditor(file) match {
        case txtEditor: TextEditor if txtEditor.getEditor != null =>
          callback(txtEditor.getEditor)
        case _ =>
      }
    }

  def getDocumentFrom(project: Project,  file: VirtualFile): Option[Document] = {
    val fileOpt = Option(PsiManager.getInstance(project).findFile(file))
    fileOpt.map { file =>
      PsiDocumentManager.getInstance(project).getCachedDocument(file)
    }
  }
}