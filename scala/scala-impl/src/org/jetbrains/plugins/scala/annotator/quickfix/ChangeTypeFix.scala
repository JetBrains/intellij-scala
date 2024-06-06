package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInsight.intention.{FileModifier, IntentionAction}
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeElementFromText
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}

/**
 * @param widenNewType true to replace singleton types with wider version, e.g. use String instead of "42" (see SCL-16489)
 */
final class ChangeTypeFix(
  typeElement: ScTypeElement,
  newTypeOriginal: ScType,
  widenNewType: Boolean = true
) extends IntentionAction {

  private lazy val newType: ScType =
    if (widenNewType) newTypeOriginal.widen
    else newTypeOriginal

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
    if (!IntentionPreviewUtils.prepareElementForWrite(typeElement.getContainingFile)) return
    if (typeElement.getParent == null || typeElement.getParent.getNode == null) return
    val replaced = typeElement.replace(createTypeElementFromText(newType.canonicalText(typeElement), typeElement)(file))
    ScalaPsiUtil.adjustTypes(replaced)
    UndoUtil.markPsiFileForUndo(file)
  }

  override def getFileModifierForPreview(target: PsiFile): FileModifier =
    new ChangeTypeFix(PsiTreeUtil.findSameElementInCopy(typeElement, target), newType)
}
