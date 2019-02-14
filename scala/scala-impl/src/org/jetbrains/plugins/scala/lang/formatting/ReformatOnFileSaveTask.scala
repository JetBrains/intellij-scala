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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaFmtPreFormatProcessor
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
      if isScalafmtEnabled && isFileSupported(vFile) && isInProjectSources(vFile)
      psiFile <- PsiDocumentManager.getInstance(project).getPsiFile(document).nullSafe
    } CommandProcessor.runUndoTransparentAction {
      ScalaFmtPreFormatProcessor.formatWithoutCommit(psiFile, respectProjectMatcher = true)
    }
  }

  private def isScalafmtEnabled: Boolean = {
    val scalaSettings = CodeStyle.getSettings(project).getCustomSettings(classOf[ScalaCodeStyleSettings])
    scalaSettings.SCALAFMT_REFORMAT_ON_FILES_SAVE && scalaSettings.USE_SCALAFMT_FORMATTER()
  }

  private def isInProjectSources(vFile: VirtualFile): Boolean = {
    ProjectRootManager.getInstance(project).getFileIndex.isInSource(vFile)
  }

  private def isFileSupported(file: VirtualFile): Boolean = isScala(file) || isSbt(file)
  private def isScala(file: VirtualFile): Boolean = file.getFileType.getName.equalsIgnoreCase("scala")
  private def isSbt(file: VirtualFile): Boolean = file.getFileType.getName.equalsIgnoreCase("sbt")
}