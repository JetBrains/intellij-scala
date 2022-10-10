package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import com.intellij.ide.actions.{SaveAllAction, SaveDocumentAction}
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, AnActionResult, CommonDataKeys}
import com.intellij.openapi.command.CommandProcessor.{getInstance => CommandProcessor}
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.{FileDocumentManager, FileEditorManager, TextEditor}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.extensions.IterableOnceExt
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.processors.ScalaFmtPreFormatProcessor
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

final class ScalafmtReformatOnFileSaveTask extends AnActionListener {

  override def afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult): Unit = {
    val project = event.getProject
    if (project == null || project.isDisposed)
      return

    action match {
      case _: SaveDocumentAction => //no shortcut by default
        val editor = event.getDataContext.getData(CommonDataKeys.EDITOR)
        if (editor != null) {
          reformatIfNeededInUndoTransparentAction(Seq(editor.getDocument))(project)
        }
      case _: SaveAllAction => //Ctrl+S or Cmd+S by default
        //NOTE: for now we reformat only selected editor, because it was how it worked before
        //But actually "Save All" action saves all documents.
        //Maybe it makes sense to reformat all open editors? Maybe add an extra setting for that?
        //Anyway it's not for 2022.2 release, maybe it's ok to have it later
        val reformatOnlySelectedEditor = true
        val editors =
          if (reformatOnlySelectedEditor) Option(FileEditorManager.getInstance(project).getSelectedEditor).toSeq
          else FileEditorManager.getInstance(project).getAllEditors.toSeq

        val documents = editors.filterByType[TextEditor].map(_.getEditor.getDocument)
        reformatIfNeededInUndoTransparentAction(documents)(project)
      case _ =>
    }
  }

  // for now 'reformat on file save' is only implemented for scalafmt formatter
  private def reformatIfNeededInUndoTransparentAction(documents: Seq[Document])(implicit project: Project): Unit = {
    if (project.isDisposed || !isScalafmtSaveOnfileEnabled(project))
      return

    val fileDocumentManager = FileDocumentManager.getInstance

    val currentTimeMs = System.currentTimeMillis()

    //debouncing: don't let "format on save" action be invoked too frequently
    if (currentTimeMs > ScalafmtReformatOnFileSaveTask.LastFormatOnSaveTimeMs + ScalafmtReformatOnFileSaveTask.MinTimeBetweenFormatOnSaveActionMs) {
      CommandProcessor.runUndoTransparentAction { () =>
        documents.foreach { document =>
          reformat(document, fileDocumentManager)
        }
      }
      ScalafmtReformatOnFileSaveTask.LastFormatOnSaveTimeMs = currentTimeMs
    }
  }

  private def reformat(document: Document, fileDocumentManager: FileDocumentManager)(implicit project: Project): Unit = {
    val vFile = fileDocumentManager.getFile(document)
    if (vFile == null)
      return

    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
    if (psiFile == null)
      return

    if (isFileSupported(psiFile) && isInProjectSources(psiFile, vFile)) {
      ScalaFmtPreFormatProcessor.formatWithoutCommit(psiFile, document, respectProjectMatcher = true)
    }
  }

  private def isScalafmtSaveOnfileEnabled(project: Project): Boolean = {
    val scalaSettings = ScalaCodeStyleSettings.getInstance(project)
    scalaSettings.SCALAFMT_REFORMAT_ON_FILES_SAVE && scalaSettings.USE_SCALAFMT_FORMATTER()
  }

  private def isInProjectSources(psiFile: PsiFile, vFile: VirtualFile)(implicit project: Project): Boolean = {
    val fileIndex = ProjectRootManager.getInstance(project).getFileIndex
    //noinspection ApiStatus
    if (org.jetbrains.sbt.internal.InternalDynamicLinker.checkIsSbtFile(psiFile)) fileIndex.isInContent(vFile)
    else fileIndex.isInSourceContent(vFile)
  }

  private def isFileSupported(file: PsiFile): Boolean =
    file.isInstanceOf[ScalaFile]
}

object ScalafmtReformatOnFileSaveTask {

  /**
   * Timestamp (e.g. System.currentTimeMillis) of the last invocation "Save All" or "Save Document" action.
   * The primary purpose of it is to debounce "reformat on save" action if user invokes it to frequently.
   *
   * NOTE: I intentionally decided that it would be enough to have a global static variable for all documents in all projects.
   * This was done not to overcomplicate the architecture.
   * "Save" actions are not supposed to be invoked too frequently by user so this should be enough for our needs.
   */
  private var LastFormatOnSaveTimeMs: Long = 0

  private def MinTimeBetweenFormatOnSaveActionMs: Int = {
    RegistryManager.getInstance().get("scalafmt.min.time.between.format.on.save.action.ms").asInteger()
  }
}