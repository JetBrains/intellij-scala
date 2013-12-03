package org.jetbrains.plugins.scala
package annotator
package quickfix

import lang.psi.api.toplevel.typedef.ScTemplateDefinition
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import lang.psi.api.base.types.ScTypeElement
import lang.psi.types.ScType
import lang.psi.impl.ScalaPsiElementFactory
import lang.psi.ScalaPsiUtil
import com.intellij.codeInsight.{FileModificationService, CodeInsightUtilBase}
import com.intellij.openapi.command.undo.UndoUtil

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
    typeElement.replace(newTypeElement)
    ScalaPsiUtil.adjustTypes(newTypeElement)
    UndoUtil.markPsiFileForUndo(file)
  }
}