package org.jetbrains.plugins.scala
package worksheet
package actions

import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.text.StringUtil.notNullize
import org.jetbrains.plugins.scala.actions.ScalaActionUtil.enableAndShowIfInScalaFile

/**
 * User: Dmitry.Naydanov
 * Date: 26.05.14.
 */
final class CreateLightWorksheetAction extends AnAction(
  ScalaBundle.message("create.light.scala.worksheet.menu.action.text"),
  ScalaBundle.message("create.light.scala.worksheet.menu.action.description"),
  /*icon = */ null
) {

  override def actionPerformed(event: AnActionEvent): Unit = {
    val project = event.getProject
    val text = event.getData(CommonDataKeys.EDITOR) match {
      case null => null
      case editor => editor.getSelectionModel.getSelectedText
    }

    ScratchRootType.getInstance.createScratchFile(
      project,
      "scratch" + ScalaFileType.INSTANCE.getExtensionWithDot,
      ScalaLanguage.INSTANCE,
      notNullize(text)
    ) match {
      case null =>
      case file => FileEditorManager.getInstance(project).openFile(file, true)
    }
  }

  override def update(event: AnActionEvent): Unit = {
    enableAndShowIfInScalaFile(event)
  }
}
