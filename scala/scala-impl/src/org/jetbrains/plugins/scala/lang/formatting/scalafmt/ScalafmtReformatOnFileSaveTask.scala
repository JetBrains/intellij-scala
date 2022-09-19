package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import com.intellij.AppTopics
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor.{getInstance => CommandProcessor}
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.{FileDocumentManager, FileDocumentManagerListener}
import com.intellij.openapi.project.{Project, ProjectManagerListener}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.processors.ScalaFmtPreFormatProcessor
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.ProjectExt

final class ScalafmtReformatOnFileSaveTask extends ProjectManagerListener {

  override def projectOpened(project: Project): Unit = {
    val bus = ApplicationManager.getApplication.getMessageBus
    bus.connect(project.unloadAwareDisposable).subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerListener {
      override def beforeAllDocumentsSaving(): Unit = project.selectedDocument match {
          case Some(document) => reformatIfNeeded(document)(project)
          case _ =>
        }
    })
  }

  // for now 'reformat on file save' is only implemented for scalafmt formatter
  private def reformatIfNeeded(document: Document)(implicit project: Project): Unit = {
    if (project.isDisposed || !isScalafmtSaveOnfileEnabled(project)) return

    for {
      vFile <- FileDocumentManager.getInstance.getFile(document).nullSafe
      psiFile <- PsiDocumentManager.getInstance(project).getPsiFile(document).nullSafe
      if isFileSupported(psiFile) && isInProjectSources(psiFile, vFile)
    } {
      CommandProcessor.runUndoTransparentAction { () =>
        ScalaFmtPreFormatProcessor.formatWithoutCommit(psiFile, document, respectProjectMatcher = true)
      }
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

  private def isFileSupported(file: PsiFile): Boolean = {
    file match {
      case sf: ScalaFile =>
        // script file formatting can only be done explicitly by calling reformat action on worksheet file
        // this is because it involves some hacks with file tree modification, that is prohibited in file save hook
        !sf.isScriptFile
      case _ =>
        false
    }
  }
}