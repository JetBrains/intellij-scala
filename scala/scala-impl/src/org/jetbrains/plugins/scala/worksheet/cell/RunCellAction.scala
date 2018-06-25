package org.jetbrains.plugins.scala.worksheet.cell

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.{FileEditorManager, TextEditor}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompilerUtil.RunOuter

/**
  * User: Dmitry.Naydanov
  * Date: 05.06.18.
  */
class RunCellAction(cellDescriptor: CellDescriptor) extends AnAction("Run cell") {
  override def actionPerformed(anActionEvent: AnActionEvent): Unit = {
    val file = cellDescriptor.getElement match {
      case Some(element) => element.getContainingFile
      case _ => return
    }
    
    FileEditorManager.getInstance(file.getProject).getSelectedEditor(file.getVirtualFile) match {
      case txt: TextEditor => txt.getEditor match {
        case ext: EditorEx =>
          new WorksheetCompiler(
            ext, 
            file.asInstanceOf[ScalaFile],
            (_, _) => {},
            false
          ).compileAndRunCode(RunOuter(cellDescriptor.getCellText))
        case _ => 
      }
      case _ => 
    }
  }
}
