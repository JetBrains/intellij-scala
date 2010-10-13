package org.jetbrains.plugins.scala.lang.formatting.processors

import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiFile, PsiElement}
import com.intellij.psi.codeStyle.CodeStyleSettings

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaBracePostFormatProcessor extends PostFormatProcessor {
  def processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange = {
    new ScalaBraceEnforcer(settings).processText(source, rangeToReformat)
  }

  def processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = {
    new ScalaBraceEnforcer(settings).process(source)
  }
}