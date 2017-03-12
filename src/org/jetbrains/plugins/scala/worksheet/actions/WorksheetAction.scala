package org.jetbrains.plugins.scala.worksheet.actions

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
  * User: Dmitry.Naydanov
  * Date: 28.02.17.
  */
trait WorksheetAction {
  this: AnAction => 
  
  def acceptFile(file: ScalaFile) = true
  
  def updateInner(e: AnActionEvent) {
    val presentation = e.getPresentation
    val project = e.getProject
    
    if (project == null) return 

    val editor = FileEditorManager.getInstance(project).getSelectedTextEditor
    if (editor == null) {
      ScalaActionUtil.disablePresentation(presentation)
      return
    } 

    extensions.inReadAction {
      PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument) match {
        case sf: ScalaFile if sf.isWorksheetFile && acceptFile(sf) => 
          ScalaActionUtil.enablePresentation(presentation)
        case _ => 
          ScalaActionUtil.disablePresentation(presentation)
      }
    }
  }
}
