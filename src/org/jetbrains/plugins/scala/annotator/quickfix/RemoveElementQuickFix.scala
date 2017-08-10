package org.jetbrains.plugins.scala
package annotator.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}

/**
 * Pavel Fatin
 */

class RemoveElementQuickFix(element: PsiElement, description: String) extends IntentionAction {

  override def getText: String = description

  override def getFamilyName = ""

  override def isAvailable(project: Project, editor: Editor, file: PsiFile) = true

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    element.delete()
  }

  override def startInWriteAction() = true
}
