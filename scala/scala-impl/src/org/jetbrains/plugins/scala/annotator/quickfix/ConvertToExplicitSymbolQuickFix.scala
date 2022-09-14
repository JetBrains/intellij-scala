package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.intention.{FileModifier, IntentionAction}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScSymbolLiteral
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

final class ConvertToExplicitSymbolQuickFix(symbolLiteral: ScSymbolLiteral) extends IntentionAction {
  private val symbolText = symbolLiteral.contentText

  override def getText: String = ScalaBundle.message("convert.to.explicit.symbol", symbolText)

  override def getFamilyName: String = ScalaBundle.message("convert.to.explicit.symbol.family")

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    symbolLiteral.isValid

  override def invoke(project: Project,
                      editor: Editor,
                      file: PsiFile): Unit =
    if (symbolLiteral.isValid) {
      val newText = s"""Symbol("$symbolText")"""
      symbolLiteral.replace {
        ScalaPsiElementFactory.createExpressionFromText(newText)(symbolLiteral.getManager)
      }
    }

  override def startInWriteAction: Boolean = true

  override def getFileModifierForPreview(target: PsiFile): FileModifier =
    new ConvertToExplicitSymbolQuickFix(PsiTreeUtil.findSameElementInCopy(symbolLiteral, target))
}
