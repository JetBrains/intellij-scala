package org.jetbrains.plugins.scala
package worksheet
package actions

import java.{util => ju}

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.fileEditor._
import com.intellij.openapi.project.DumbService.DumbModeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiDocumentManager, PsiManager}
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.{UserDataHolderExt, UserDataKeys}
import org.jetbrains.plugins.scala.worksheet.actions.repl.WorksheetReplRunAction
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.StopWorksheetAction.StoppableProcess
import org.jetbrains.plugins.scala.worksheet.interactive.WorksheetAutoRunner
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterFactory
import org.jetbrains.plugins.scala.worksheet.ui.{WorksheetControlPanel, WorksheetFoldGroup}

import scala.ref.WeakReference
import scala.util.control.NonFatal

object WorksheetFileHook {

  private val file2panel = ContainerUtil.createWeakValueMap[VirtualFile, WorksheetControlPanel]

  private def getAndRemovePanel(file: VirtualFile): Option[WorksheetControlPanel] =
    Option(file2panel.remove(file))

  private def getPanel(file: VirtualFile): Option[WorksheetControlPanel] =
    Option(file2panel.get(file))

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

  @CalledInAwt()
  def disableRun(file: VirtualFile, exec: Option[StoppableProcess]): Unit =
    WorksheetFileHook.getPanel(file).foreach(_.disableRun(exec))

  @CalledInAwt()
  def enableRun(file: VirtualFile, hasErrors: Boolean): Unit =
    WorksheetFileHook.getPanel(file).foreach(_.enableRun(hasErrors))

  def updateStoppableProcess(file: VirtualFile, exec: Option[StoppableProcess]): Unit =
    invokeLater {
      WorksheetFileHook.getPanel(file).foreach(_.updateStoppableProcess(exec))
    }

  def isRunning(file: VirtualFile): Boolean =
    WorksheetFileHook.getPanel(file).exists(!_.isRunEnabled)

  private class WorksheetEditorListener(project: Project) extends FileEditorManagerListener {

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

      initWorksheetComponents(file)
      loadEvaluationResult(source, file)
      val document = WorksheetFileHook.getDocumentFrom(source.getProject, file)
      document.foreach(WorksheetAutoRunner.getInstance(source.getProject).addListener(_))
    }

    private def ensureWorksheetModuleIsSet(file: ScalaFile): Unit =
      for {
        module <- Option(WorksheetFileSettings(file).getModuleFor)
      } file.getOrUpdateUserData(UserDataKeys.SCALA_ATTACHED_MODULE, new WeakReference(module))

    private def loadEvaluationResult(source: FileEditorManager, vFile: VirtualFile): Unit =
      for {
        editor    <- editorWithFile(source, vFile)
        manager   = PsiDocumentManager.getInstance(project)
        scalaFile <- Option(manager.getPsiFile(editor.getDocument)).filterByType[ScalaFile]
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
                case NonFatal(_) => //ignored; if we are trying to load code stored in "plain" mode while in REPL mode
              }
            }
          }
        case _ =>
      }
    }

    private def initWorksheetComponents(file: VirtualFile): Unit = {
      if (project.isDisposed) return

      val myFileEditorManager = FileEditorManager.getInstance(project)
      val editors = myFileEditorManager.getAllEditors(file)

      for (editor <- editors) {
        WorksheetFileHook.getAndRemovePanel(file).foreach { panel =>
          invokeLater {
            myFileEditorManager.removeTopComponent(editor, panel)
          }
        }

        val controlPanel = new WorksheetControlPanel(file)
        val actions: ju.List[AnAction] = ContainerUtil.immutableSingletonList(new WorksheetReplRunAction)
        UIUtil.putClientProperty(editor.getComponent, AnAction.ACTIONS_KEY, actions)
        file2panel.put(file, controlPanel)
        myFileEditorManager.addTopComponent(editor, controlPanel)
      }
    }

  }

  private class WorksheetDumbModeListener(project: Project) extends DumbModeListener {
    override def enteredDumbMode(): Unit = {}
    override def exitDumbMode(): Unit = initializeButtons()

    private def initializeButtons(): Unit =
      for {
        editor <- Option(FileEditorManager.getInstance(project).getSelectedTextEditor)
        file   <- Option(PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument))
        vFile  <- Option(file.getVirtualFile)
        panel  <- WorksheetFileHook.getPanel(vFile)
      } notifyButtons(panel)

    private def notifyButtons(panel: WorksheetControlPanel): Unit =
      panel.getComponents.foreach {
        case button: ActionButton =>
          button.addNotify()
        case _ =>
      }
  }
}