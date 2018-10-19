package org.jetbrains.plugins.scala
package lang
package formatting
package settings

import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.application.options.{CodeStyleAbstractConfigurable, CodeStyleAbstractPanel}
import com.intellij.lang.Language
import com.intellij.openapi.options.Configurable
import com.intellij.psi.codeStyle.{CodeStyleSettings, CodeStyleSettingsProvider, DisplayPriority}

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.07.2008
 */
 
class ScalaCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
  override def getConfigurableDisplayName: String = ScalaBundle.message("title.scala.settings")

  override def createSettingsPage(settings: CodeStyleSettings, originalSettings: CodeStyleSettings): Configurable = {
    new CodeStyleAbstractConfigurable(settings, originalSettings, "Scala") {
      protected def createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel = {
        panel = new ScalaTabbedCodeStylePanel(getCurrentSettings, settings)
        panel
      }

      override def setModel(model: CodeStyleSchemesModel): Unit = {
        super.setModel(model)
        panel.onProjectSet(model.getProject)
      }

      private var panel: ScalaTabbedCodeStylePanel = _
    }
  }

  override def getPriority: DisplayPriority = {
    DisplayPriority.COMMON_SETTINGS
  }

  override def getLanguage: Language = ScalaLanguage.INSTANCE

  override def createCustomSettings(settings: CodeStyleSettings) = new ScalaCodeStyleSettings(settings)
}
