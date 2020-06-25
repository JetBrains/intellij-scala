package org.jetbrains.plugins.scala.lang.formatting.processors

import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

class ScalaBracePostFormatProcessor extends PostFormatProcessor with ScalaIntellijFormatterLike {
  override def processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange = {
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    if (needToProcess(source, rangeToReformat, scalaSettings)) {
      new ScalaBraceEnforcer(settings, scalaSettings).processText(source, rangeToReformat)
    } else {
      rangeToReformat
    }
  }

  override def processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = {
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    if (needToProcess(source, source.getTextRange, scalaSettings)) {
      new ScalaBraceEnforcer(settings, scalaSettings).process(source)
    } else {
      source
    }
  }
}