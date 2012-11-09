package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.ide.fileTemplates.actions.CreateFromTemplateAction
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.scala.icons.Icons

/**
 * @author Ksenia.Sautina
 * @since 10/30/12
 */

class NewScalaWorksheetAction extends CreateFromTemplateAction(FileTemplateManager.getInstance().getInternalTemplate("Scala Worksheet"))
with DumbAware {
  override def update(e: AnActionEvent) {
    super.update(e)
    //todo logo
//    e.getPresentation.setIcon(Icons.SCRIPT_FILE_LOGO)
  }
}