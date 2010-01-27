package org.jetbrains.plugins.scala.actions

import com.intellij.psi.codeStyle.{CodeStyleSettingsManager, CodeStyleSettings}
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import com.intellij.openapi.editor.Editor
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.FileContentUtil
import java.util.ArrayList

/**
 * User: Alexander Podkhalyuzin
 * Date: 27.01.2010
 */

class EnableErrorHighlightingAction extends AnAction {
  def actionPerformed(e: AnActionEvent): Unit = {
    val context: DataContext = e.getDataContext
    val project = PlatformDataKeys.PROJECT.getData(context)
    val settings = CodeStyleSettingsManager.getSettings(project).
            getCustomSettings(classOf[ScalaCodeStyleSettings])
    val enable = settings.ENABLE_ERROR_HIGHLIGHTING
    settings.ENABLE_ERROR_HIGHLIGHTING = !enable
    PlatformDataKeys.VIRTUAL_FILE.getData(context) match {
      case null => return
      case file: VirtualFile => {
        val list = new ArrayList[VirtualFile]
        list.add(file)
        FileContentUtil.reparseFiles(project, list, true)
      }
      case _ => return
    }
  }
}