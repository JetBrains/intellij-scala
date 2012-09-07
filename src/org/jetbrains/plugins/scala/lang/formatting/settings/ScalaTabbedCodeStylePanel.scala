package org.jetbrains.plugins.scala.lang.formatting.settings

import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.application.options._
import com.intellij.psi.codeStyle.CodeStyleSettings

/**
 * User: Alefas
 * Date: 23.09.11
 */
class ScalaTabbedCodeStylePanel(currentSettings: CodeStyleSettings, settings: CodeStyleSettings)
  extends TabbedLanguageCodeStylePanel(ScalaFileType.SCALA_LANGUAGE, currentSettings, settings) {
  protected override def initTabs(settings: CodeStyleSettings) {
    super.initTabs(settings)
    addTab(new MultiLineStringCodeStylePanel(settings))
    addTab(new TypeAnnotationsPanel(settings))
  }
}