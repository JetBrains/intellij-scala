package org.jetbrains.plugins.scala
package lang
package formatting
package settings

import com.intellij.psi.codeStyle.{CodeStyleSettings, CustomCodeStyleSettings, CodeStyleSettingsProvider}
import com.intellij.openapi.options.Configurable
/**
 * User: Alexander Podkhalyuzin
 * Date: 28.07.2008
 */
 
class ScalaCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
  override def createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings = {
    new ScalaCodeStyleSettings(settings)
  }

  def createSettingsPage(settings: CodeStyleSettings, originalSettings: CodeStyleSettings): Configurable = {
    return new ScalaFormatConfigurable(settings, originalSettings)
  }
}
