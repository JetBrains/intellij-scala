package org.jetbrains.plugins.scala
package codeInspection
package caseClassParamInspection

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScForBinding, ScGenerator}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter

class RemoveValQuickFix(param: ScClassParameter)
        extends AbstractFixOnPsiElement(ScalaBundle.message("remove.val"), param) {

  override protected def doApplyFix(p: ScClassParameter)
                                   (implicit project: Project): Unit = {
    p.findChildrenByType(ScalaTokenTypes.kVAL).foreach(_.delete())
    CodeStyleManager.getInstance(p.getProject).reformatText(p.getContainingFile,
      p.getModifierList.getTextRange.getStartOffset,
      p.getModifierList.getTextRange.getEndOffset)
  }
}

class RemoveValFromForBindingIntentionAction(forBinding: ScForBinding) extends IntentionAction {

  override def getText: String = ScalaInspectionBundle.message("remove.unnecessary.val")

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    if (!forBinding.isValid) return
    removeVal(forBinding)
  }

  override def startInWriteAction(): Boolean = true

  override def getFamilyName: String = ScalaInspectionBundle.message("remove.val.from.definition")

  override def generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo = {
    removeVal(PsiTreeUtil.findSameElementInCopy(forBinding, file))
    IntentionPreviewInfo.DIFF
  }

  private def removeVal(binding: ScForBinding): Unit =
    binding.findChildrenByType(ScalaTokenTypes.kVAL).foreach(_.delete())
}

class RemoveValFromGeneratorIntentionAction(generator: ScGenerator) extends IntentionAction {

  override def getText: String = ScalaInspectionBundle.message("remove.unnecessary.val")

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    if (!generator.isValid) return
    removeVal(generator)
  }

  override def startInWriteAction() = true

  override def getFamilyName: String = ScalaInspectionBundle.message("remove.val.from.definition")

  override def generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo = {
    removeVal(PsiTreeUtil.findSameElementInCopy(generator, file))
    IntentionPreviewInfo.DIFF
  }

  private def removeVal(gen: ScGenerator): Unit =
    gen.findChildrenByType(ScalaTokenTypes.kVAL).foreach(_.delete())
}
