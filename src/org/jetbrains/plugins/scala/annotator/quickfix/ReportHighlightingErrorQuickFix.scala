package org.jetbrains.plugins.scala
package annotator
package quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Pavel Fatin
 */

object ReportHighlightingErrorQuickFix extends IntentionAction {
  def getText: String = ScalaBundle.message("report.highlighting.error.fix")

  def startInWriteAction: Boolean = false

  def isAvailable(project: Project, editor: Editor, file: PsiFile) = true

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    DesktopUtils.browse("http://youtrack.jetbrains.net/issues/SCL#newissue=yes")
  }

  def getFamilyName: String = ScalaBundle.message("report.highlighting.error.fix")
}