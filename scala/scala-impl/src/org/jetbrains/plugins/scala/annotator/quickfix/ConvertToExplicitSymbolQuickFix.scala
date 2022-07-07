package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
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
    if (symbolLiteral.isValid) {
      val newText = s"""Symbol("$symbolText")"""
      symbolLiteral.replace {
        ScalaPsiElementFactory.createExpressionFromText(newText)(symbolLiteral.getManager)
      }
    }

  override final def startInWriteAction: Boolean = true
}
