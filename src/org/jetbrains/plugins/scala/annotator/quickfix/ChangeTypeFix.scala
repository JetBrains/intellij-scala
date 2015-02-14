package org.jetbrains.plugins.scala
package annotator
package quickfix

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScType

class ChangeTypeFix(typeElement: ScTypeElement, newType: ScType) extends IntentionAction {
  val getText: String = "Change type '%s' to '%s'".format(typeElement.getText, newType.presentableText)

  def getFamilyName: String = "Change Type"

  def startInWriteAction: Boolean = true

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = typeElement.isValid && typeElement.getManager.isInProject(file)

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    if (!typeElement.isValid) return
    if (!FileModificationService.getInstance.prepareFileForWrite(typeElement.getContainingFile)) return
    if (typeElement.getParent == null || typeElement.getParent.getNode == null) return
    val newTypeElement = ScalaPsiElementFactory.createTypeElementFromText(newType.canonicalText, typeElement.getManager)
    val replaced = typeElement.replace(newTypeElement)
    ScalaPsiUtil.adjustTypes(replaced)
    UndoUtil.markPsiFileForUndo(file)
  }
}