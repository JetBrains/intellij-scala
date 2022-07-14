package org.jetbrains.plugins.scala
package annotator
package quickfix

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeElementFromText
import org.jetbrains.plugins.scala.lang.psi.types.api.TypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}

class ChangeTypeFix(typeElement: ScTypeElement, newType: ScType) extends IntentionAction {

  override val getText: String = {
    implicit val tpc: TypePresentationContext = TypePresentationContext(typeElement)
    val (oldTypeDescripton, newTypeDescription) = typeElement.`type`() match {
      case Right(oldType) => TypePresentation.different(oldType, newType)
      case _ => (typeElement.getText, newType.presentableText)
    }
    ScalaBundle.message("change.type.to", oldTypeDescripton, newTypeDescription)
  }

  override def getFamilyName: String = ScalaBundle.message("family.name.change.type")

  override def startInWriteAction: Boolean = true

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    typeElement.isValid

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    if (!typeElement.isValid) return
    if (!FileModificationService.getInstance.prepareFileForWrite(typeElement.getContainingFile)) return
    if (typeElement.getParent == null || typeElement.getParent.getNode == null) return
    val replaced = typeElement.replace(createTypeElementFromText(newType.canonicalText)(typeElement.getManager))
    ScalaPsiUtil.adjustTypes(replaced)
    UndoUtil.markPsiFileForUndo(file)
  }
}