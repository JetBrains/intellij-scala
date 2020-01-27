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
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.processors.scalafmt.ScalaFmtPreFormatProcessor
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.sbt.language.SbtFileImpl

class ReformatOnFileSaveTask(project: Project) extends ProjectComponent {

  override def initComponent(): Unit = {
    val bus = ApplicationManager.getApplication.getMessageBus
    bus.connect(project).subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerListener {
      override def beforeDocumentSaving(document: Document): Unit = reformatIfNeeded(document)
    })
  }

  // for now 'reformat on file save' is only implemented for scalafmt formatter
  private def reformatIfNeeded(document: Document): Unit = {
    if (project.isDisposed || !isScalafmtEnabled) return

    for {
      vFile <- FileDocumentManager.getInstance.getFile(document).nullSafe
      psiFile <- PsiDocumentManager.getInstance(project).getPsiFile(document).nullSafe
      if isFileSupported(psiFile) && isInProjectSources(psiFile, vFile)
    } CommandProcessor.runUndoTransparentAction { () =>
      ScalaFmtPreFormatProcessor.formatWithoutCommit(psiFile, document, respectProjectMatcher = true)
    }
  }

  private def isScalafmtEnabled: Boolean = {
    val scalaSettings = ScalaCodeStyleSettings.getInstance(project)
    scalaSettings.SCALAFMT_REFORMAT_ON_FILES_SAVE && scalaSettings.USE_SCALAFMT_FORMATTER()
  }

  private def isInProjectSources(psiFile: PsiFile, vFile: VirtualFile): Boolean = {
    val fileIndex = ProjectRootManager.getInstance(project).getFileIndex
    psiFile match {
      case _ :SbtFileImpl => fileIndex.isInContent(vFile)
      case _ => fileIndex.isInSourceContent(vFile)
    }
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