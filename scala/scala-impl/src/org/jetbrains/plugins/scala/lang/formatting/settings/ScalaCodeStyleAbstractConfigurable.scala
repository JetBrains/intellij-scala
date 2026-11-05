package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.application.options.{CodeStyleAbstractConfigurable, CodeStyleAbstractPanel}
import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.jetbrains.plugins.scala.help.ScalaWebHelpProvider
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleAbstractConfigurable.Log

private class ScalaCodeStyleAbstractConfigurable(settings: CodeStyleSettings, cloneSettings: CodeStyleSettings)
  extends CodeStyleAbstractConfigurable(settings, cloneSettings, "Scala") {

  override def getHelpTopic: String =
    ScalaWebHelpProvider.HelpPrefix + "troubleshoot-common-scala-issues.html#type_aware_highlighting"

  override protected def createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel = {
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

  private var panel: ScalaTabbedCodeStylePanel = scala.compiletime.uninitialized
}

private object ScalaCodeStyleAbstractConfigurable {
  private val Log = Logger.getInstance(classOf[ScalaLanguageCodeStyleSettingsProvider])
}
