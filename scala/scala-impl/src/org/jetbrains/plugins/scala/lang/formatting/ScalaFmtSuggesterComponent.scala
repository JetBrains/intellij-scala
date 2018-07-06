package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{DialogWrapper, VerticalFlowLayout}
import com.intellij.ui.components.{JBCheckBox, JBLabel}
import javax.swing.{JComponent, JPanel}
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaFmtPreFormatProcessor
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

class ScalaFmtSuggesterComponent(val project: Project) extends AbstractProjectComponent(project) {
  override def projectOpened(): Unit = {
    val settings = ScalaCodeStyleSettings.getInstance(project)
    if  (!settings.USE_SCALAFMT_FORMATTER && ScalaFmtPreFormatProcessor.projectDefaultConfig(project).nonEmpty) {
      val enableScalafmt = new JBCheckBox("Enable scalafmt for this project", false)
      val enableAutoDetection = new JBCheckBox("Enable automatic scalafmt detection", false)
      val disableSuggester = new JBCheckBox("Never show this dialog again", false)
      //suggest the feature automatically
      if (settings.SUGGEST_AUTO_DETECT_SCALAFMT && !settings.AUTO_DETECT_SCALAFMT) {
        val dialog = new DialogWrapper(project) {
          init()
          override def createCenterPanel(): JComponent = {
            val topPanel = new JPanel(new VerticalFlowLayout)
            topPanel.add(new JBLabel("Scalafmt configuration file has been detected in the project. Would you like IDEA to automatically switch to scalafmt when applicable?"))
            topPanel.add(enableScalafmt)
            topPanel.add(enableAutoDetection)
            topPanel.add(disableSuggester)
            topPanel
          }
          override def doOKAction(): Unit = {
            settings.AUTO_DETECT_SCALAFMT = enableAutoDetection.isSelected
            settings.SUGGEST_AUTO_DETECT_SCALAFMT = !disableSuggester.isSelected
            super.doOKAction()
          }
        }
        dialog.setTitle("Scalafmt auto-detection")
        dialog.showAndGet()
      }
      if (settings.AUTO_DETECT_SCALAFMT || enableScalafmt.isSelected) {
        val codeStyleSchemesModel = new CodeStyleSchemesModel(project)
        var scheme = codeStyleSchemesModel.getSelectedScheme
        if (!codeStyleSchemesModel.isProjectScheme(scheme)) {
          codeStyleSchemesModel.copyToProject(scheme)
          scheme = codeStyleSchemesModel.getProjectScheme
        }
        val newSettings = scheme.getCodeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
        newSettings.USE_SCALAFMT_FORMATTER = true
        codeStyleSchemesModel.apply()
      }
    }
  }
}
