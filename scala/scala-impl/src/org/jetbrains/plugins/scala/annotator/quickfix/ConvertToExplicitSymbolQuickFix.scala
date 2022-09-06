package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScSymbolLiteral
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class ConvertToExplicitSymbolQuickFix(symbolLiteral: ScSymbolLiteral) extends IntentionAction {
  private val symbolText = symbolLiteral.contentText

  override def getText: String = ScalaBundle.message("convert.to.explicit.symbol", symbolText)

  override final def getFamilyName: String = ScalaBundle.message("convert.to.explicit.symbol.family")

  override final def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    symbolLiteral.isValid

  override final def invoke(project: Project,
                            editor: Editor,
                            file: PsiFile): Unit =
    if (symbolLiteral.isValid) doReplaceSymbol(symbolLiteral)

  override final def startInWriteAction: Boolean = true


  override def generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo = {
    doReplaceSymbol(PsiTreeUtil.findSameElementInCopy(symbolLiteral, file))
    IntentionPreviewInfo.DIFF
  }

  private def doReplaceSymbol(symbol: ScSymbolLiteral): Unit = {
    val newText = s"""Symbol("$symbolText")"""
    symbol.replace {
      ScalaPsiElementFactory.createExpressionFromText(newText)(symbol.getManager)
    }
  }
}
