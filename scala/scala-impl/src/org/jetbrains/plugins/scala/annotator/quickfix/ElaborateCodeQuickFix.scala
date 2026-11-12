package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.semantic.ElaborateCodeAction

object ElaborateCodeQuickFix extends IntentionAction {
  override def getText: String = ScalaBundle.message("elaborate.code.quick.fix")

  override def startInWriteAction: Boolean = false

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    !file.is[ScalaCodeFragment]

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    new ElaborateCodeAction().perform(project, editor, file.asInstanceOf[ScalaFile])
  }

  override def getFamilyName: String = getText
}
