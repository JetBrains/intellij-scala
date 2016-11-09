package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.ide.scratch.{ScratchFileService, ScratchRootType}
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.actions.ScalaActionUtil

/**
 * User: Dmitry.Naydanov
 * Date: 26.05.14.
 */
class CreateLightWorksheetAction extends AnAction {
  override def actionPerformed(e: AnActionEvent) {
    val project = e.getProject
    val editor = e getData CommonDataKeys.EDITOR
    val text = StringUtil.notNullize(if (editor == null) null else editor.getSelectionModel.getSelectedText)
    
    val f: VirtualFile = ScratchRootType.getInstance.createScratchFile(
      project, "scratch", ScalaLanguage.INSTANCE, text, ScratchFileService.Option.create_new_always)
    if (f != null) FileEditorManager.getInstance(project).openFile(f, true)
  }

  override def update(e: AnActionEvent) {
    ScalaActionUtil enableAndShowIfInScalaFile e
  }
}
