package org.jetbrains.plugins.scala
package worksheet
package actions

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.openapi.actionSystem.{ActionToolbar, AnAction}
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor._
import com.intellij.openapi.project.DumbService.DumbModeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiDocumentManager, PsiManager}
import com.intellij.ui.ClientProperty
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.worksheet.actions.repl.WorksheetReplRunAction
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.StopWorksheetAction.StoppableProcess
import org.jetbrains.plugins.scala.worksheet.interactive.WorksheetAutoRunner
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterFactory
import org.jetbrains.plugins.scala.worksheet.ui.{WorksheetControlPanel, WorksheetDiffSplitters, WorksheetFoldGroup}

import java.{util => ju}
import scala.util.Using
import scala.util.control.NonFatal

object WorksheetFileHook {

  private val file2panel = ContainerUtil.createWeakValueMap[VirtualFile, WorksheetControlPanel]

  private def getAndRemovePanel(file: VirtualFile): Option[WorksheetControlPanel] =
    Option(file2panel.remove(file))

  private def getPanel(file: VirtualFile): Option[WorksheetControlPanel] =
    Option(file2panel.get(file))

  private def getDocumentFrom(project: Project, file: VirtualFile): Option[Document] = {
    val fileOpt = Option(PsiManager.getInstance(project).findFile(file))
    fileOpt.flatMap { file =>
      //getCachedDocument can return `null` for example in some tests where a non-physical file is used
      Option(PsiDocumentManager.getInstance(project).getCachedDocument(file))
    }
  }

  @RequiresEdt()
  def disableRun(file: VirtualFile, exec: Option[StoppableProcess]): Unit =
    WorksheetFileHook.getPanel(file).foreach(_.disableRun(exec))

  @RequiresEdt()
  def enableRun(file: VirtualFile, hasErrors: Boolean): Unit =
    WorksheetFileHook.getPanel(file).foreach(_.enableRun(hasErrors))

  def updateStoppableProcess(file: VirtualFile, exec: Option[StoppableProcess]): Unit =
    invokeLater {
      WorksheetFileHook.getPanel(file).foreach(_.updateStoppableProcess(exec))
    }

  def isRunning(file: VirtualFile): Boolean =
    WorksheetFileHook.getPanel(file).exists(!_.isRunEnabled)

  def moduleUpdated(project: Project, virtualFile: VirtualFile): Unit = {
    WorksheetSyntheticModuleService(project).moduleUpdated(virtualFile)
    restartFileAnalyzing(project, virtualFile)
  }

  private def restartFileAnalyzing(project: Project, virtualFile: VirtualFile): Unit = {
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
    if (psiFile != null) {
      psiFile match {
        case scalaFile: ScalaFile =>
          scalaFile.incContextModificationStamp()
        case _ =>
      }
      DaemonCodeAnalyzerEx.getInstanceEx(project).restart(psiFile)
    }
  }

  def profileUpdated(project: Project, virtualFile: VirtualFile): Unit = {
    /**
     * Currently we have only module/project-level caching for profiles
     * (see caching in [[org.jetbrains.plugins.scala.lang.resolve.processor.precedence.PrecedenceTypes.forModule]])
     * (see caching in [[org.jetbrains.plugins.scala.project.ModuleExt.scalaModuleSettings]])
     *
     * If project compiler profiles configuration is not changed it means that no changes were made in any module profile.
     * Worksheet profile "lives" out of scope of [[org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration]]
     * (see [[org.jetbrains.plugins.scala.worksheet.actions.WorksheetSyntheticModule.compilerProfileName]])
     * So to trigger correct file re-highlighting we need to invalidate some compiler-options-specific caches.
     */
    ScalaCompilerConfiguration.incModificationCount()
    restartFileAnalyzing(project, virtualFile)
  }

  final class WorksheetBeforeEditorOpenedOrClosedListener(project: Project) extends FileEditorManagerListener.Before {
    override def beforeFileClosed(source: FileEditorManager, file: VirtualFile): Unit = {
      if (!isPluggable(file)) return

     val selectedEditor = source.getSelectedTextEditor
     if (selectedEditor != null) {
       val cache = WorksheetCache.getInstance(project)
       cache.editorClosed(selectedEditor)
     }
    }

    private def isPluggable(file: VirtualFile): Boolean = file.isValid &&
      WorksheetUtils.isWorksheetFile(project, file)
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  final class WorksheetEditorListener(project: Project) extends FileEditorManagerListener {

    private def isPluggable(file: VirtualFile): Boolean = file.isValid &&
      WorksheetUtils.isWorksheetFile(project, file)

    override def selectionChanged(event: FileEditorManagerEvent): Unit = {}

    override def fileClosed(source: FileEditorManager, file: VirtualFile): Unit = {
      if (!isPluggable(file)) return

      val project = source.getProject
      val document = WorksheetFileHook.getDocumentFrom(project, file)
      document.foreach(WorksheetAutoRunner.getInstance(project).removeListener(_))
    }

    override def fileOpened(source: FileEditorManager, file: VirtualFile): Unit = {
      if (!isPluggable(file)) return

      val project = source.getProject

      WorksheetFileSettings(project, file).ensureSettingsArePersisted()
      WorksheetSyntheticModuleService(project).ensureWorksheetModuleAttachedToPsiFile(file)
      initWorksheetUiComponents(file)
      loadEvaluationResult(project, source, file)

      val document = WorksheetFileHook.getDocumentFrom(project, file)
      document.foreach(WorksheetAutoRunner.getInstance(project).addListener(_))
    }

    private def loadEvaluationResult(project: Project, source: FileEditorManager, file: VirtualFile): Unit =
      for {
        editor <- editorWithFile(source, file)
      } loadEvaluationResult(project, file, editor)

    private def editorWithFile(source: FileEditorManager, file: VirtualFile): Option[EditorEx] =
      Option(source.getSelectedEditor(file))
        .collect { case te: TextEditor => te.getEditor }
        .collect { case e: EditorEx => e }

    private def loadEvaluationResult(project: Project, file: VirtualFile, editor: EditorEx): Unit = {
      val evaluationResultOpt = WorksheetEditorPrinterFactory.loadWorksheetEvaluation(file)
      evaluationResultOpt.foreach {
        case (result, ratio) if result.nonEmpty =>
          val viewer = WorksheetEditorPrinterFactory.createViewer(editor)
          val document = viewer.getDocument

          val splitterOpt = WorksheetDiffSplitters.getSplitter(editor)

          inWriteAction {
            document.setText(result)
            PsiDocumentManager.getInstance(project).commitDocument(document)

            splitterOpt.foreach { splitter =>
              try {
                splitter.setProportion(ratio)
                val group = WorksheetFoldGroup.load(viewer, editor, project, splitter, file)
                WorksheetEditorPrinterFactory.synch(editor, viewer, Some(splitter), Some(group))
              } catch {
                case NonFatal(_) => //ignored; if we are trying to load code stored in "plain" mode while in REPL mode
              }
            }
          }
        case _ =>
      }
    }

    private def initWorksheetUiComponents(file: VirtualFile): Unit = {
      if (project.isDisposed) return

      val myFileEditorManager = FileEditorManager.getInstance(project)
      val editors = myFileEditorManager.getAllEditors(file)

      for (editor <- editors) {
        WorksheetFileHook.getAndRemovePanel(file).foreach { panel =>
          invokeLater {
            myFileEditorManager.removeTopComponent(editor, panel)
          }
        }

        val controlPanel = new WorksheetControlPanel()
        val actions: ju.List[AnAction] = ju.List.of(new WorksheetReplRunAction)
        ClientProperty.put(editor.getComponent, AnAction.ACTIONS_KEY, actions)
        file2panel.put(file, controlPanel)
        myFileEditorManager.addTopComponent(editor, controlPanel)
      }
    }
  }

  final class WorksheetDumbModeListener(project: Project) extends DumbModeListener {
    override def enteredDumbMode(): Unit = {}
    override def exitDumbMode(): Unit = {
      // exitDumbMode event arrives on the EDT, initializeButtons uses file index to get a file
      Using.resource(SlowOperations.knownIssue("SCL-21147"))(_ => initializeButtons())
    }

    private def initializeButtons(): Unit =
      for {
        editor <- Option(FileEditorManager.getInstance(project).getSelectedTextEditor)
        file   <- Option(PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument))
        vFile  <- Option(file.getVirtualFile)
        panel  <- WorksheetFileHook.getPanel(vFile)
      } notifyButtons(panel)

    private def notifyButtons(panel: WorksheetControlPanel): Unit =
      panel.getComponents.foreach {
        case toolbar: ActionToolbar =>
          toolbar.addNotify()
        case _ =>
      }
  }
}
