package org.jetbrains.plugins.scala
package annotator
package quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.openapi.ui.Messages
import lang.formatting.settings.ScalaCodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.util.FileContentUtil
import collection.JavaConversions._

/**
 * Pavel Fatin
 */

object DisableTypeAwareHighlightingQuickFix extends IntentionAction {
  def getText: String = ScalaBundle.message("disable.type.aware.highlighting.fix")

  def startInWriteAction: Boolean = false

  def isAvailable(project: Project, editor: Editor, file: PsiFile) = true

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    val settings = CodeStyleSettingsManager.getSettings(project).getCustomSettings(classOf[ScalaCodeStyleSettings])
    settings.ENABLE_ERROR_HIGHLIGHTING = false

    FileContentUtil.reparseFiles(project, Seq(file.getVirtualFile), true)

    Messages.showInfoMessage(
      "Type-aware highlighting has been disabled\n" +
              "(it may be re-enabled in Project Settigns / Code Style / Scala / Other Settings\n" +
              "or using Ctrl+Alt+Shift+E shortcut)",
      "Type-aware highlighting")
  }

  def getFamilyName: String = ScalaBundle.message("disable.type.aware.highlighting.fix")
}