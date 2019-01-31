package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.AppTopics
import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor.{getInstance => CommandProcessor}
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.{FileDocumentManager, FileDocumentManagerListener}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.processors.{ScalaFmtConfigUtil, ScalaFmtPreFormatProcessor}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

class ReformatOnFileSaveTask(project: Project) extends ProjectComponent {

  override def initComponent(): Unit = {
    val bus = ApplicationManager.getApplication.getMessageBus
    bus.connect(project).subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerListener {
      override def beforeDocumentSaving(document: Document): Unit = reformatIfNeeded(document)
    })
  }

  // for now 'reformat on file save' is only implemented for scalafmt formatter
  private def reformatIfNeeded(document: Document): Unit = {
    if (project.isDisposed) return

    for {
      vFile <- FileDocumentManager.getInstance.getFile(document).nullSafe
      if ProjectRootManager.getInstance(project).getFileIndex.isInSource(vFile)
      if ScalaFmtConfigUtil.isFileSupported(vFile)
      scalaSettings = CodeStyle.getSettings(project).getCustomSettings(classOf[ScalaCodeStyleSettings])
      if scalaSettings.SCALAFMT_REFORMAT_ON_FILES_SAVE && scalaSettings.USE_SCALAFMT_FORMATTER()
      psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
      if ScalaFmtConfigUtil.isIncludedInProject(psiFile)
    } {
      formatFile(psiFile)
    }
  }

  private def formatFile(psiFile: PsiFile): Unit = {
    CommandProcessor.runUndoTransparentAction {
      ScalaFmtPreFormatProcessor.formatWithoutCommit(psiFile)
    }
  }
}