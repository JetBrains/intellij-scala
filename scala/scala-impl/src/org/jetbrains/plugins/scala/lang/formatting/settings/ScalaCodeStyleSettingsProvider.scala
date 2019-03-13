package org.jetbrains.plugins.scala
package lang
package formatting
package settings

import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.application.options.{CodeStyleAbstractConfigurable, CodeStyleAbstractPanel}
import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.Configurable
import com.intellij.psi.codeStyle.{CodeStyleSettings, CodeStyleSettingsProvider, DisplayPriority}

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.07.2008
 */

class ScalaCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
  override def getConfigurableDisplayName: String = ScalaBundle.message("title.scala.settings")

  private val Log = Logger.getInstance(getClass)

  override def createSettingsPage(settings: CodeStyleSettings, originalSettings: CodeStyleSettings): Configurable = {
    new CodeStyleAbstractConfigurable(settings, originalSettings, "Scala") {
      protected def createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel = {
        panel = try new ScalaTabbedCodeStylePanel(getCurrentSettings, settings) catch {
          case ex: Throwable =>
            Log.error("Error occurred during scala code style panel initialization", ex)
            throw ex
        }
        panel
      }

      override def setModel(model: CodeStyleSchemesModel): Unit = {
        super.setModel(model)
        panel.onProjectSet(model.getProject)
        panel.onModelSet(model)
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
