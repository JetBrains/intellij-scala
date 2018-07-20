package org.jetbrains.plugins.scala.lang.formatting.settings

import java.awt._
import java.awt.event.ItemEvent

import com.intellij.application.options._
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
class ScalaTabbedCodeStylePanel(currentSettings: CodeStyleSettings, settings: CodeStyleSettings, project: () => Option[Project])
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
    val scalaCodeStyleSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    super.isModified(settings) || scalaCodeStyleSettings.FORMATTER != formatters(useExternalFormatterCheckbox.getSelectedItem.toString) ||
      shortenedPanel.exposeIsModified(settings)
  }

  override def apply(settings: CodeStyleSettings): Unit = {
    super.apply(settings)
    val scalaCodeStyleSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    scalaCodeStyleSettings.FORMATTER = formatters(useExternalFormatterCheckbox.getSelectedItem.toString)
    if (scalaCodeStyleSettings.USE_SCALAFMT_FORMATTER) shortenedPanel.exposeApply(settings)
  }

  override def resetImpl(settings: CodeStyleSettings): Unit = {
    super.resetImpl(settings)
    val scalaCodeStyleSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    useExternalFormatterCheckbox.setSelectedItem(formatters.find(_._2 == scalaCodeStyleSettings.FORMATTER).map(_._1).get)
    shortenedPanel.exposeResetImpl(settings)
    if (scalaCodeStyleSettings.USE_SCALAFMT_FORMATTER) toggleSettingsVisibility(scalaCodeStyleSettings.USE_SCALAFMT_FORMATTER)
  }

  private def initOuterFormatterPanel(): Unit = {
    outerPanel = new JPanel(new GridLayoutManager(7, 2, new Insets(0, 0, 0, 0), -1, -1))
    useExternalFormatterCheckbox = new ComboBox(formatters.keys.toArray)
    val formatterSelectorPanel = new JPanel(new GridLayoutManager(1, 3, new Insets(0, 10, 0, 0), -1, -1))
    outerPanel.add(formatterSelectorPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
      null, 0, false))
    formatterSelectorPanel.add(new JBLabel("Formatter:"), new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
      null, 0, false))
    formatterSelectorPanel.add(useExternalFormatterCheckbox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
      GridConstraints.SIZEPOLICY_FIXED, null, null,
      null, 0, false))
    formatterSelectorPanel.add(new Spacer, new GridConstraints(0, 2, 1, 1,
      GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW,
      GridConstraints.SIZEPOLICY_CAN_SHRINK, null, null, null, 0, false))
    outerPanel.add(innerPanel,
      new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
        null, 0, false))
    outerPanel.add(shortenedPanel.getPanel,
      new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
        null, 0, false))
    useExternalFormatterCheckbox.addItemListener((_: ItemEvent) => {
      //USE_SCALAFMT_FORMATTER setting is immediately set to allow proper formatting for core formatter examples
      val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
      val oldFormatter = scalaSettings.FORMATTER
      scalaSettings.FORMATTER = formatters(useExternalFormatterCheckbox.getSelectedItem.toString)
      if (scalaSettings.FORMATTER != oldFormatter) toggleSettingsVisibility(scalaSettings.USE_SCALAFMT_FORMATTER)
    })
    toggleSettingsVisibility(false)
  }

  private def toggleSettingsVisibility(useExternalFormatter: Boolean): Unit = {
    innerPanel.setVisible(!useExternalFormatter)
    shortenedPanel.getPanel.setVisible(useExternalFormatter)
    val tempSettings = settings.clone()
    if (useExternalFormatter) {
      apply(tempSettings)
      shortenedPanel.exposeResetImpl(tempSettings)
    } else {
      shortenedPanel.exposeApply(tempSettings)
      resetImpl(tempSettings)
    }
  }

  private var useExternalFormatterCheckbox: JComboBox[String] = _
  private var outerPanel: JPanel = _
  private def innerPanel = super.getPanel

  override def getPanel: JComponent = outerPanel

  private lazy val shortenedPanel = new TabbedLanguageCodeStylePanel(ScalaLanguage.INSTANCE, currentSettings, settings) {
    protected override def initTabs(settings: CodeStyleSettings): Unit = {
      addTab(new ScalaFmtSettingsPanel(settings, project))
      addTab(new ImportsPanel(settings))
      addTab(new MultiLineStringCodeStylePanel(settings))
      addTab(new TypeAnnotationsPanel(settings))
      addTab(new ScalaArrangementPanel(settings))
      val otherCodeStylePanel: OtherCodeStylePanel = new OtherCodeStylePanel(settings)
      addTab(otherCodeStylePanel)
      otherCodeStylePanel.toggleExternalFormatter(true)
    }

    def exposeIsModified(settings: CodeStyleSettings) = super.isModified(settings)
    def exposeApply(settings: CodeStyleSettings) = super.apply(settings)
    def exposeResetImpl(settings: CodeStyleSettings) = super.resetImpl(settings)
  }

  override def dispose(): Unit = {
    super.dispose()
    shortenedPanel.dispose()
  }
}

object ScalaTabbedCodeStylePanel {
  private val formatters: Map[String, Int] = Map(("IntelliJ", ScalaCodeStyleSettings.INTELLIJ_FORMATTER), ("scalafmt", ScalaCodeStyleSettings.SCALAFMT_FORMATTER))
}