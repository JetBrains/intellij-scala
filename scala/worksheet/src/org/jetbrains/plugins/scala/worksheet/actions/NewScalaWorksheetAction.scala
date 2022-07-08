package org.jetbrains.plugins.scala
package worksheet
package actions

import com.intellij.ide.fileTemplates.actions.AttributesDefaults
import com.intellij.openapi.actionSystem.{AnActionEvent, CommonDataKeys, DataContext, PlatformCoreDataKeys}
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiDirectory, PsiFile}
import org.jetbrains.plugins.scala.actions.LazyFileTemplateAction
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.worksheet.actions.NewScalaWorksheetAction._

final class NewScalaWorksheetAction extends LazyFileTemplateAction(
  "Scala Worksheet",
  WorksheetBundle.message("new.scalaworksheet.menu.action.text"),
  WorksheetBundle.message("new.scalaworksheet.menu.action.description"),
  WorksheetFileType.getIcon
) {

  override def getAttributesDefaults(dataContext: DataContext): AttributesDefaults = {
    val selectedElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext)
    val attributes = selectedElement match {
      case directory: PsiDirectory =>
        suggestIndex(directory.getVirtualFile).headOption
      case file: PsiFile =>
        val parent = Option(file.getVirtualFile.getParent)
        parent.flatMap(suggestIndex(_).headOption)
      case _ =>
        None
    }
    attributes.orNull
  }

  override def update(event: AnActionEvent): Unit = {
    super.update(event)

    val isEnabled = event.getDataContext.getData(PlatformCoreDataKeys.MODULE.getName) match {
      case module: Module => module.hasScala
      case _ => false
    }

    val presentation = event.getPresentation
    presentation.setEnabled(isEnabled)
    presentation.setVisible(isEnabled)
    presentation.setIcon(icon)
  }
}

object NewScalaWorksheetAction {

  private def suggestIndex(file: VirtualFile) = for {
    index <- "" #:: LazyList.tabulate(1000)(index => (index + 1).toString)
    defaultName = "untitled" + index

    if file.findChild(defaultName + "." + WorksheetFileType.getDefaultExtension) == null
  } yield new AttributesDefaults(defaultName)
}