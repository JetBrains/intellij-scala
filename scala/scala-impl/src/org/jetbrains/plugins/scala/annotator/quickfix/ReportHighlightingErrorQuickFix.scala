package org.jetbrains.plugins.scala
package annotator
package quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment

object ReportHighlightingErrorQuickFix extends IntentionAction {

  override def getText: String = ScalaBundle.message("report.highlighting.error.fix")

  override def startInWriteAction: Boolean = false

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    if (file.is[ScalaCodeFragment]) return false
    true
  }

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit =
    BrowserUtil.browse("https://youtrack.jetbrains.net/issues/SCL#newissue=yes")

  override def getFamilyName: String = ScalaBundle.message("report.highlighting.error.fix")
}
