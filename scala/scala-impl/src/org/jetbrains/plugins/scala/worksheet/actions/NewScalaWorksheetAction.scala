package org.jetbrains.plugins.scala
package worksheet
package actions

import com.intellij.ide.fileTemplates.actions.AttributesDefaults
import com.intellij.openapi.actionSystem.{AnActionEvent, CommonDataKeys, DataContext, LangDataKeys}
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import org.jetbrains.plugins.scala.actions.LazyFileTemplateAction
import org.jetbrains.plugins.scala.project._

/**
 * @author Ksenia.Sautina
 * @since 10/30/12
 */
final class NewScalaWorksheetAction extends LazyFileTemplateAction("Scala Worksheet", WorksheetFileType.getIcon) {

  import NewScalaWorksheetAction._

  override def getAttributesDefaults(dataContext: DataContext): AttributesDefaults =
    (CommonDataKeys.PSI_ELEMENT.getData(dataContext) match {
      case directory: PsiDirectory => suggestIndex(directory.getVirtualFile).headOption
      case _ => None
    }).orNull

  override def update(event: AnActionEvent): Unit = {
    super.update(event)

    val isEnabled = event.getDataContext.getData(LangDataKeys.MODULE.getName) match {
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
    index <- "" #:: Stream.tabulate(1000)(index => (index + 1).toString)
    defaultName = "untitled" + index

    if file.findChild(defaultName + "." + WorksheetFileType.getDefaultExtension) == null
  } yield new AttributesDefaults(defaultName)
}