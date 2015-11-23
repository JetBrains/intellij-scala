package org.jetbrains.plugins.scala
package actions

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateAction
import com.intellij.openapi.actionSystem.{AnActionEvent, LangDataKeys}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project._

/**
 * Pavel Fatin
 */

class NewScalaScriptAction
        extends CreateFromTemplateAction(FileTemplateManager.getDefaultInstance().getInternalTemplate("Scala Script"))
        with DumbAware {
  override def update(e: AnActionEvent) {
    super.update(e)
    val module: Module = e.getDataContext.getData(LangDataKeys.MODULE.getName).asInstanceOf[Module]
    val isEnabled: Boolean = Option(module).exists(_.hasScala)
    e.getPresentation.setEnabled(isEnabled)
    e.getPresentation.setVisible(isEnabled)
    e.getPresentation.setIcon(Icons.SCRIPT_FILE_LOGO)
  }
}