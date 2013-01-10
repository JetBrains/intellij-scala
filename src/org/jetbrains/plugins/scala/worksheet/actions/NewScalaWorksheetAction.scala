package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.ide.fileTemplates.actions.CreateFromTemplateAction
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.actionSystem.{LangDataKeys, AnActionEvent}
import org.jetbrains.plugins.scala.icons.Icons
import config.ScalaFacet
import com.intellij.openapi.module.Module

/**
 * @author Ksenia.Sautina
 * @since 10/30/12
 */

class NewScalaWorksheetAction extends CreateFromTemplateAction(FileTemplateManager.getInstance().getInternalTemplate("Scala Worksheet"))
with DumbAware {
  override def update(e: AnActionEvent) {
    super.update(e)
    val module: Module = e.getDataContext.getData(LangDataKeys.MODULE.getName).asInstanceOf[Module]
    val isEnabled: Boolean = if (module == null) false else ScalaFacet.isPresentIn(module)
    e.getPresentation.setEnabled(isEnabled)
    e.getPresentation.setVisible(isEnabled)
    e.getPresentation.setIcon(Icons.WORKSHEET_LOGO)
  }
}