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
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeElementFromText
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.result.Success

class ChangeTypeFix(typeElement: ScTypeElement, newType: ScType) extends IntentionAction {

  val getText: String = {
    val (oldTypeDescripton, newTypeDescription) = typeElement.getType() match {
      case Success(oldType, _) => ScTypePresentation.different(oldType, newType)
      case _ => (typeElement.getText, newType.presentableText)
    }
    s"Change type '$oldTypeDescripton' to '$newTypeDescription'"
  }

  def getFamilyName: String = "Change Type"

  def startInWriteAction: Boolean = true

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = typeElement.isValid && typeElement.getManager.isInProject(file)

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    if (!typeElement.isValid) return
    if (!FileModificationService.getInstance.prepareFileForWrite(typeElement.getContainingFile)) return
    if (typeElement.getParent == null || typeElement.getParent.getNode == null) return
    val replaced = typeElement.replace(createTypeElementFromText(newType.canonicalText)(typeElement.getManager))
    ScalaPsiUtil.adjustTypes(replaced)
    UndoUtil.markPsiFileForUndo(file)
  }
}