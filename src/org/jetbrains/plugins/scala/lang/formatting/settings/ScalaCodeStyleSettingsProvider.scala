package org.jetbrains.plugins.scala
package lang
package formatting
package settings

import com.intellij.openapi.options.Configurable
import java.lang.String
import com.intellij.application.options.{CodeStyleAbstractPanel, CodeStyleAbstractConfigurable}
import com.intellij.psi.codeStyle.{DisplayPriority, CodeStyleSettings, CustomCodeStyleSettings, CodeStyleSettingsProvider}
import com.intellij.util.PlatformUtils
import settings.ScalaCodeStyleSettings

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

  override def getLanguage = ScalaFileType.SCALA_LANGUAGE

  override def createCustomSettings(settings: CodeStyleSettings) = new ScalaCodeStyleSettings(settings)
}
