package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.psi.codeStyle.{CodeStyleConfigurable, CodeStyleSettings}

private final class ScalaBackendLanguageCodeStyleSettingsProvider extends ScalaLanguageCodeStyleSettingsProvider {
    override def createConfigurable(baseSettings: CodeStyleSettings, modelSettings: CodeStyleSettings): CodeStyleConfigurable =
      new ScalaCodeStyleAbstractConfigurable(baseSettings, modelSettings)
}
