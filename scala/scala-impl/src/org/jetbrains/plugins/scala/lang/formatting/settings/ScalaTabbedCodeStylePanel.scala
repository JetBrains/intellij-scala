package org.jetbrains.plugins.scala.lang.formatting.settings

import java.awt._
import java.awt.event.ItemEvent

import com.intellij.application.options._
import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.ui.components.JBLabel
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager, Spacer}
import javax.swing._
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.rearranger.ScalaArrangementPanel

/**
 * User: Alefas
 * Date: 23.09.11
 */
class ScalaTabbedCodeStylePanel(currentSettings: CodeStyleSettings, settings: CodeStyleSettings)
  extends TabbedLanguageCodeStylePanel(ScalaLanguage.INSTANCE, currentSettings, settings) {

  import ScalaTabbedCodeStylePanel._

  protected override def initTabs(settings: CodeStyleSettings) {
    super.initTabs(settings)
    addTab(new ScalaDocFormattingPanel(settings))
    addTab(new ImportsPanel(settings))
    addTab(new MultiLineStringCodeStylePanel(settings))
    addTab(new TypeAnnotationsPanel(settings))
    addTab(new ScalaArrangementPanel(settings))
    addTab(new OtherCodeStylePanel(settings))
    initOuterFormatterPanel()
  }

  override def isModified(settings: CodeStyleSettings): Boolean = {
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])

    val formatterTypeChanged = scalaSettings.FORMATTER != getSelectedFormatterId
    formatterTypeChanged ||
      super.isModified(settings) ||
      shortenedPanel.exposeIsModified(settings)
  }

  private def getSelectedFormatterId: Int =
    formatters(formatterSelectorComboBox.getSelectedItem.toString)

  override def apply(settings: CodeStyleSettings): Unit = {
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])

    scalaSettings.FORMATTER = getSelectedFormatterId

    if (scalaSettings.USE_SCALAFMT_FORMATTER){
      shortenedPanel.exposeApply(settings)
    } else {
      super.apply(settings)
    }

    syncPanels(scalaSettings)
  }

  override def resetImpl(settings: CodeStyleSettings): Unit = {
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])

    val formatterKey = formatters.find(_._2 == scalaSettings.FORMATTER).map(_._1).get
    formatterSelectorComboBox.setSelectedItem(formatterKey)

    toggleSettingsVisibility(scalaSettings.USE_SCALAFMT_FORMATTER)

    if (scalaSettings.USE_SCALAFMT_FORMATTER) {
      shortenedPanel.exposeResetImpl(settings)
    } else {
      super.resetImpl(settings)
    }

    syncPanels(scalaSettings)
  }

  private def initOuterFormatterPanel(): Unit = {
    formatterSelectorComboBox = new ComboBox(formatters.keys.toArray)
    formatterSelectorComboBox.addItemListener((_: ItemEvent) => {
      //USE_SCALAFMT_FORMATTER setting is immediately set to allow proper formatting for core formatter examples
      val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
      val oldFormatter = scalaSettings.FORMATTER
      scalaSettings.FORMATTER = getSelectedFormatterId
      if (scalaSettings.FORMATTER != oldFormatter) {
        toggleSettingsVisibility(scalaSettings.USE_SCALAFMT_FORMATTER)
        syncPanels(scalaSettings)
      }
    })

    import GridConstraints._
    val CAN_SHRINK_AND_GROW = SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW

    def constraint(row: Int, col: Int, fill: Int, HSizePolicy: Int, VSizePolicy: Int) =
      new GridConstraints(row, col, 1, 1, ANCHOR_CENTER, fill, HSizePolicy, VSizePolicy, null, null, null, 0, false)

    val formatterSelectorPanel = new JPanel(new GridLayoutManager(1, 3, new Insets(0, 10, 0, 0), -1, -1))
    formatterSelectorPanel.add(new JBLabel("Formatter:"), constraint(0, 0, FILL_BOTH, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
    formatterSelectorPanel.add(formatterSelectorComboBox, constraint(0, 1, FILL_HORIZONTAL, CAN_SHRINK_AND_GROW, SIZEPOLICY_FIXED))
    formatterSelectorPanel.add(new Spacer, constraint(0, 2, FILL_HORIZONTAL, SIZEPOLICY_WANT_GROW, SIZEPOLICY_CAN_SHRINK))

    outerPanel = new JPanel(new GridLayoutManager(7, 2, new Insets(0, 0, 0, 0), -1, -1))
    outerPanel.add(formatterSelectorPanel, constraint(0, 0, FILL_BOTH, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
    outerPanel.add(innerPanel, constraint(1, 0, FILL_BOTH, CAN_SHRINK_AND_GROW, CAN_SHRINK_AND_GROW))
    outerPanel.add(shortenedPanel.getPanel, constraint(2, 0, FILL_BOTH, CAN_SHRINK_AND_GROW, CAN_SHRINK_AND_GROW))

    toggleSettingsVisibility(false)
  }

  def onProjectSet(project: Project): Unit = {
    scalaFmtSettingsPanel.onProjectSet(project)
  }

  private def syncPanels(scalaSettings: ScalaCodeStyleSettings): Unit = {
    val tempSettings = settings.clone()
    if (scalaSettings.USE_SCALAFMT_FORMATTER) {
      shortenedPanel.exposeApply(tempSettings)
      super.resetImpl(tempSettings)
    } else {
      super.apply(tempSettings)
      shortenedPanel.exposeResetImpl(tempSettings)
    }
  }

  private def toggleSettingsVisibility(useExternalFormatter: Boolean): Unit = {
    innerPanel.setVisible(!useExternalFormatter)
    shortenedPanel.getPanel.setVisible(useExternalFormatter)
  }

  private var formatterSelectorComboBox: JComboBox[String] = _
  private var outerPanel: JPanel = _
  private var scalaFmtSettingsPanel: ScalaFmtSettingsPanel = _
  private def innerPanel: JComponent = super.getPanel

  override def getPanel: JComponent = outerPanel

  private lazy val shortenedPanel = new TabbedLanguageCodeStylePanel(ScalaLanguage.INSTANCE, currentSettings, settings) {
    protected override def initTabs(settings: CodeStyleSettings): Unit = {
      scalaFmtSettingsPanel = new ScalaFmtSettingsPanel(settings)
      addTab(scalaFmtSettingsPanel)
      addTab(new ImportsPanel(settings))
      addTab(new MultiLineStringCodeStylePanel(settings))
      addTab(new TypeAnnotationsPanel(settings))
      addTab(new ScalaArrangementPanel(settings))
      val otherCodeStylePanel: OtherCodeStylePanel = new OtherCodeStylePanel(settings)
      addTab(otherCodeStylePanel)
      otherCodeStylePanel.toggleExternalFormatter(true)
    }

    def exposeIsModified(settings: CodeStyleSettings): Boolean = super.isModified(settings)
    def exposeApply(settings: CodeStyleSettings): Unit = super.apply(settings)
    def exposeResetImpl(settings: CodeStyleSettings): Unit = super.resetImpl(settings)
  }

  override def dispose(): Unit = {
    super.dispose()
    shortenedPanel.dispose()
  }
}

object ScalaTabbedCodeStylePanel {
  private val formatters: Map[String, Int] = Map(
    "IntelliJ" -> ScalaCodeStyleSettings.INTELLIJ_FORMATTER,
    "scalafmt" -> ScalaCodeStyleSettings.SCALAFMT_FORMATTER
  )
}