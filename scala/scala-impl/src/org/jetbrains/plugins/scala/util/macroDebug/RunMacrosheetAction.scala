package org.jetbrains.plugins.scala
package util.macroDebug

import com.intellij.icons.AllIcons
import com.intellij.lang.{Language, StdLanguages}
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.application.{ApplicationManager, ModalityState}
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiFile, PsiFileFactory}
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import worksheet.actions.{CleanWorksheetAction, TopComponentAction}
import worksheet.ui.WorksheetEditorPrinterFactory

/**
 * @author Ksenia.Sautina
 * @author Dmitry Naydanov        
 * @since 10/17/12
 */

class RunMacrosheetAction extends AnAction with TopComponentAction {
  def createBlankEditor(project: Project, defaultText: String = "", lang: Language = StdLanguages.TEXT): Editor = {
    val editor = EditorFactory.getInstance.createViewer(PsiDocumentManager.getInstance(project).getDocument(
      PsiFileFactory.getInstance(project).createFileFromText("dummy", lang, defaultText)), project)
    editor setBorder null
    editor
  }

  def actionPerformed(e: AnActionEvent) {
    val editor = FileEditorManager.getInstance(e.getProject).getSelectedTextEditor
    if (editor == null) return

    val psiFile: PsiFile = PsiDocumentManager.getInstance(e.getProject).getPsiFile(editor.getDocument)
    psiFile match {
      case file: ScalaFile =>
        val viewer = WorksheetEditorPrinterFactory.getMacrosheetUiFor(editor, file).getViewerEditor

        val project = e.getProject

        if (viewer != null) {
          ApplicationManager.getApplication.invokeAndWait(new Runnable {
            override def run() {
              extensions.inWriteAction {
                CleanWorksheetAction.resetScrollModel(viewer)
                CleanWorksheetAction.cleanWorksheet(file.getNode, editor, viewer, project)
              }
            }
          }, ModalityState.any())
        }

        ScalaMacroDebuggingUtil.macrosToExpand.clear()
        ScalaMacroDebuggingUtil.allMacroCalls.foreach(ScalaMacroDebuggingUtil.macrosToExpand.add)
        ScalaMacroDebuggingUtil.expandMacros(project)

      case _ =>
    }
  }

  override def update(e: AnActionEvent) {
    ScalaActionUtil.enableAndShowIfInScalaFile(e)
  }

  override def actionIcon = AllIcons.Actions.Execute

  override def bundleKey = "worksheet.execute.button"

  override def shortcutId: Option[String] = Some("Scala.RunWorksheet")
}