package org.jetbrains.plugins.scala.lang.formatting.processors

import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

class ScalaBracePostFormatProcessor extends PostFormatProcessor {
  def processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange = {
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    if (scalaSettings.USE_SCALAFMT_FORMATTER()) {
      rangeToReformat
    } else {
      new ScalaBraceEnforcer(settings, scalaSettings).processText(source, rangeToReformat)
    }
  }

  def processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = {
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    if (scalaSettings.USE_SCALAFMT_FORMATTER()) {
      source
    } else {
      new ScalaBraceEnforcer(settings, scalaSettings).process(source)
    }
  }
}