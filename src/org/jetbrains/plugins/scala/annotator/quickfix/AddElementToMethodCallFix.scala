package org.jetbrains.plugins.scala
package annotator.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

/**
 * Created by Svyatoslav ILINSKIY on 7/2/2014.
 */
class AddElementToMethodCallFix(call: ScMethodCall, elementToAdd: PsiElement, nameOfElement: String) extends IntentionAction {
  override def getText: String = s"Add $nameOfElement"

  override def getFamilyName: String = getText

  override def startInWriteAction(): Boolean = true

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = call.isValid() &&
          call.getManager.isInProject(file) && elementToAdd != null

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    call.addAfter(elementToAdd, call.getLastChild)
  }
}
