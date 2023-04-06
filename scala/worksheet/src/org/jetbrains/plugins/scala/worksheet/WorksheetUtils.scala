package org.jetbrains.plugins.scala.worksheet

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.{FileEditorManager, TextEditor}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

object WorksheetUtils {

  // to test restoring of compiler messages positions in original worksheet file in one test method
  @TestOnly val ContinueOnFirstFailure = "scala.worksheet.continue.repl.evaluation.on.first.expression.failure"
  private val ShowReplErrorsInEditor = "scala.worksheet.show.repl.errors.in.editor"
  private val ShowReplErrorsInEditorInInteractiveMode = "scala.worksheet.show.repl.errors.in.editor.in.interactive.mode"

  @TestOnly def continueWorksheetEvaluationOnExpressionFailure: Boolean = Registry.is(ContinueOnFirstFailure)
  @TestOnly def showReplErrorsInEditor: Boolean = Registry.is(ShowReplErrorsInEditor)
  @TestOnly def showReplErrorsInEditorInInteractiveMode: Boolean = Registry.is(ShowReplErrorsInEditorInInteractiveMode)

  def isWorksheetFile(project: Project, file: VirtualFile): Boolean = {
    val isExplicitWorksheet = WorksheetFileType.isMyFileType(file) && !isAmmoniteEnabled(project, file)
    isExplicitWorksheet ||
      isScratchWorksheet(project, file)
  }

  def isScratchWorksheet(project: Project, file: VirtualFile): Boolean = {
    ScratchUtil.isScratch(file) && {
      WorksheetFileType.isMyFileType(file) ||
        WorksheetUtils.treatScratchFileAsWorksheet(project) && ScalaFileType.INSTANCE.isMyFileType(file)
    }
  }

  private def treatScratchFileAsWorksheet(project: Project): Boolean =
    settings(project).isTreatScratchFilesAsWorksheet

  def isAmmoniteEnabled(project: Project, file: VirtualFile): Boolean = {
    import ScalaProjectSettings.ScFileMode._
    settings(project).getScFileMode match {
      case Worksheet => false
      case Ammonite  => true
      case _         =>
        ProjectRootManager.getInstance(project).getFileIndex.isUnderSourceRootOfType(
          file,
          ContainerUtil.newHashSet(JavaSourceRootType.TEST_SOURCE)
        )
    }
  }

  private def settings(project: Project) =
    ScalaProjectSettings.getInstance(project)

  @RequiresEdt
  def getSelectedTextEditor(project: Project, file: VirtualFile): Option[Editor] =
    FileEditorManager.getInstance(project).getSelectedEditor(file) match {
      case txtEditor: TextEditor if txtEditor.getEditor != null =>
        Option(txtEditor.getEditor)
      case _ =>
        None
    }
}
