package org.jetbrains.plugins.scala.codeInspection.caseClassParamInspection

import com.intellij.codeInsight.intention.{FileModifier, IntentionAction}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScForBinding, ScGenerator}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter

final class RemoveValQuickFix(param: ScClassParameter)
  extends AbstractFixOnPsiElement(ScalaBundle.message("remove.val"), param)
    with DumbAware {
  override protected def doApplyFix(p: ScClassParameter)
                                   (implicit project: Project): Unit = {
    p.findChildrenByType(ScalaTokenTypes.kVAL).foreach(_.delete())
    CodeStyleManager.getInstance(p.getProject).reformatText(p.getContainingFile,
      p.getModifierList.getTextRange.getStartOffset,
      p.getModifierList.getTextRange.getEndOffset)
  }
}

final class RemoveValFromForBindingIntentionAction(forBinding: ScForBinding) extends IntentionAction with DumbAware {
  override def getText: String = ScalaInspectionBundle.message("remove.unnecessary.val")

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    if (!forBinding.isValid) return
    forBinding.findChildrenByType(ScalaTokenTypes.kVAL).foreach(_.delete())
  }

  override def startInWriteAction(): Boolean = true

  override def getFamilyName: String = ScalaInspectionBundle.message("remove.val.from.definition")

  override def getFileModifierForPreview(target: PsiFile): FileModifier =
    new RemoveValFromForBindingIntentionAction(PsiTreeUtil.findSameElementInCopy(forBinding, target))
}

final class RemoveValFromGeneratorIntentionAction(generator: ScGenerator) extends IntentionAction {

  override def getText: String = ScalaInspectionBundle.message("remove.unnecessary.val")

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    if (!generator.isValid) return
    generator.findChildrenByType(ScalaTokenTypes.kVAL).foreach(_.delete())
  }

  override def startInWriteAction() = true

  override def getFamilyName: String = ScalaInspectionBundle.message("remove.val.from.definition")

  override def getFileModifierForPreview(target: PsiFile): FileModifier =
    new RemoveValFromGeneratorIntentionAction(PsiTreeUtil.findSameElementInCopy(generator, target))
}
