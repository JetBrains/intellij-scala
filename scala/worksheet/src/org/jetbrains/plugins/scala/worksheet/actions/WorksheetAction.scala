package org.jetbrains.plugins.scala.worksheet.actions

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.WorksheetFile

trait WorksheetAction {
  this: AnAction => 
  
  def acceptFile(file: ScalaFile) = true
  
  def getSelectedFile(project: Project): Option[PsiFile] =
    for {
      editor <- Option(FileEditorManager.getInstance(project).getSelectedTextEditor)
      file   <- Option(PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument))
    } yield file

  protected def getSelectedFile(e: AnActionEvent): Option[PsiFile] =
    Option(e.getProject).flatMap(getSelectedFile)
  
  def updateInner(e: AnActionEvent): Unit = {
    val presentation = e.getPresentation
    if (isActionEnabled(e))
      ScalaActionUtil.enablePresentation(presentation)
    else
      ScalaActionUtil.disablePresentation(presentation)
  }

  private def isActionEnabled(e: AnActionEvent): Boolean =
    inReadAction {
      val file = getSelectedFile(e)
      file match {
        case Some(sf: WorksheetFile) =>
          acceptFile(sf)
        case _ =>
          false
      }
    }
}
