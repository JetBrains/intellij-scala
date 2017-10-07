package org.jetbrains.plugins.hocon.formatting

import com.intellij.formatting.FormattingModelBuilder
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.{FormattingDocumentModelImpl, PsiBasedFormattingModel}
import com.intellij.psi.{PsiElement, PsiFile}

class HoconFormattingModelBuilder extends FormattingModelBuilder {
  def createModel(element: PsiElement, settings: CodeStyleSettings): PsiBasedFormattingModel = {
    val containingFile = element.getContainingFile
    val block = new HoconBlock(new HoconFormatter(settings), element.getNode, null, null, null)
    new PsiBasedFormattingModel(containingFile, block, FormattingDocumentModelImpl.createOn(containingFile))
  }

  def getRangeAffectingIndent(file: PsiFile, offset: Int, elementAtOffset: ASTNode): TextRange =
    elementAtOffset.getTextRange
}
