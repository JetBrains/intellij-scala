package org.jetbrains.plugins.scala
package lang
package formatting
package settings

import com.intellij.application.options.{CodeStyleAbstractConfigurable, CodeStyleAbstractPanel}
import com.intellij.openapi.options.Configurable
import com.intellij.psi.codeStyle.{CodeStyleSettings, CodeStyleSettingsProvider, DisplayPriority}

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.07.2008
 */
 
class ScalaCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
  override def getConfigurableDisplayName: String = ScalaBundle.message("title.scala.settings")

  def createSettingsPage(settings: CodeStyleSettings, originalSettings: CodeStyleSettings): Configurable = {
    new CodeStyleAbstractConfigurable(settings, originalSettings, "Scala") {
      protected def createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel = {
        new ScalaTabbedCodeStylePanel(getCurrentSettings, settings)
      }

      def getHelpTopic: String = {
        null
      }
    }
  }

  override def getPriority: DisplayPriority = {
    DisplayPriority.COMMON_SETTINGS
  }

  override def getLanguage = ScalaLanguage.INSTANCE

  override def createCustomSettings(settings: CodeStyleSettings) = new ScalaCodeStyleSettings(settings)
}
