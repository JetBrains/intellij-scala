package org.jetbrains.plugins.scala
package actions

import com.intellij.openapi.project.DumbAware
import icons.Icons
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateAction
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Pavel Fatin
 */

class NewScalaScriptAction
        extends CreateFromTemplateAction(FileTemplateManager.getInstance().getInternalTemplate("Scala Script"))
        with DumbAware {
  override def update(e: AnActionEvent) {
    super.update(e)
    e.getPresentation.setIcon(Icons.SCRIPT_FILE_LOGO)
  }
}