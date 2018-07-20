package org.jetbrains.plugins.scala
package lang
package formatting
package settings

import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.application.options.{CodeStyleAbstractConfigurable, CodeStyleAbstractPanel}
import com.intellij.lang.Language
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.{CodeStyleSettings, CodeStyleSettingsProvider, DisplayPriority}
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaFmtConfigUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.07.2008
 */
 
class ScalaCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
  override def getConfigurableDisplayName: String = ScalaBundle.message("title.scala.settings")

  def createSettingsPage(settings: CodeStyleSettings, originalSettings: CodeStyleSettings): Configurable = {
    new CodeStyleAbstractConfigurable(settings, originalSettings, "Scala") {
      protected def createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel = {
        new ScalaTabbedCodeStylePanel(getCurrentSettings, settings, () => project)
      }

      override def setModel(model: CodeStyleSchemesModel): Unit = {
        super.setModel(model)
        project = Option(model.getProject)
        project.foreach(ScalaFmtConfigUtil.notifyNotSupportedFeatures(model.getSelectedScheme.getCodeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings]), _))
      }
    }
  }

  private var project: Option[Project] = None

  override def getPriority: DisplayPriority = {
    DisplayPriority.COMMON_SETTINGS
  }

  override def getLanguage: Language = ScalaLanguage.INSTANCE

  override def createCustomSettings(settings: CodeStyleSettings) = new ScalaCodeStyleSettings(settings)
}
