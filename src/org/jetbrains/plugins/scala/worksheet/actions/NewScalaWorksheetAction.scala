package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateAction
import com.intellij.openapi.actionSystem.{AnActionEvent, LangDataKeys}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project._

/**
 * @author Ksenia.Sautina
 * @since 10/30/12
 */

class NewScalaWorksheetAction extends CreateFromTemplateAction(FileTemplateManager.getInstance().getInternalTemplate("Scala Worksheet"))
with DumbAware {
  override def update(e: AnActionEvent) {
    super.update(e)
    val module: Module = e.getDataContext.getData(LangDataKeys.MODULE.getName).asInstanceOf[Module]
    val isEnabled: Boolean = Option(module).exists(_.hasScala)
    e.getPresentation.setEnabled(isEnabled)
    e.getPresentation.setVisible(isEnabled)
    e.getPresentation.setIcon(Icons.WORKSHEET_LOGO)
  }
}