package org.jetbrains.plugins.scala
package annotator
package quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.openapi.ui.Messages
import lang.formatting.settings.ScalaCodeStyleSettings
import com.intellij.openapi.vfs.VirtualFile
import java.util.ArrayList
import com.intellij.psi.codeStyle.CodeStyleSettingsManager

/**
 * Pavel Fatin
 */

object DisableTypeAwareHighlightingQuickFix extends IntentionAction {
  def getText: String = ScalaBundle.message("disable.type.aware.highlighting.fix")

  def startInWriteAction: Boolean = false

  def isAvailable(project: Project, editor: Editor, file: PsiFile) = true

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    Messages.showInfoMessage(
      "Type-aware highlighting is disabled now\n" +
              "(it may be re-enabled in Settigns / Code Style / Scala / Other Settings\n" +
              "or using Ctrl+Alt+Shift+E shortcut)",
      "Type-aware highlighting is disabled")
  }

  def getFamilyName: String = ScalaBundle.message("disable.type.aware.highlighting.fix")
//
//  // IN PROJECT
//  def disableTypeAwareHighlighting(project: Project) {
//    val settings = CodeStyleSettingsManager.getSettings(project).getCustomSettings(classOf[ScalaCodeStyleSettings])
//    val enable = settings.ENABLE_ERROR_HIGHLIGHTING
//    settings.ENABLE_ERROR_HIGHLIGHTING = !enable
//    PlatformDataKeys.VIRTUAL_FILE.getData(context) match {
//      case null => return
//      case file: VirtualFile => {
//        val list = new ArrayList[VirtualFile]
//        list.add(file)
//        FileContentUtil.reparseFiles(project, list, true)
//      }
//      case _ => return
//    }
//  }

//  }
}