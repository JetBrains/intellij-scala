package org.jetbrains.plugins.scala.worksheet.actions

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

trait WorksheetAction {
  this: AnAction => 
  
  def acceptFile(file: ScalaFile) = true
  
  def getSelectedFile(project: Project): Option[PsiFile] =
    for {
      editor <- Option(FileEditorManager.getInstance(project).getSelectedTextEditor)
      file   <- Option(PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument))
    } yield file
  
  def updateInner(e: AnActionEvent) {
    if (e == null) return
    
    val presentation = e.getPresentation
    val project = e.getProject
    
    if (project == null) return 
    
   inReadAction {
      getSelectedFile(project) match {
        case Some(sf: ScalaFile) if sf.isWorksheetFile && acceptFile(sf) => 
          ScalaActionUtil.enablePresentation(presentation)
        case _ => 
          ScalaActionUtil.disablePresentation(presentation)
      }
    }
  }
}
