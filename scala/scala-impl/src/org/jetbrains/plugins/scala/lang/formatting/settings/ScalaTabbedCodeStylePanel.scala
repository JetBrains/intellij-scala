package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.application.options._
import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.ui.components.JBLabel
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager, Spacer}
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.lang.rearranger.ScalaArrangementPanel
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaLanguage}

import java.awt._
import java.awt.event.ItemEvent
import javax.swing._
import scala.annotation.nowarn

class ScalaTabbedCodeStylePanel(currentSettings: CodeStyleSettings, settings: CodeStyleSettings)
  extends TabbedLanguageCodeStylePanel(ScalaLanguage.INSTANCE, currentSettings, settings) {

  import ScalaTabbedCodeStylePanel._

  private var formatterSelectorComboBox: JComboBox[String] = _
  private var outerPanel: JPanel = _
  private var shortenedPanel: ScalafmtTabbedLanguageCodeStylePanel = _
  // TODO: rework this whole project juggling mess, there should be a straightforward way of depending on a project
  //  from code style settings panels
  private var typeAnnotationsPanel: TypeAnnotationsPanel = _

  override def dispose(): Unit = {
    super.dispose()
    Disposer.dispose(shortenedPanel)
  }

  override def getPanel: JComponent = outerPanel

  private def innerPanel: JComponent = super.getPanel

  override protected def initTabs(settings: CodeStyleSettings): Unit = {
    super.initTabs(settings)
    addTab(new ScalaDocFormattingPanel(settings))
    addTab(new ImportsPanel(settings))
    addTab(new MultiLineStringCodeStylePanel(settings))
    addTab({typeAnnotationsPanel = new TypeAnnotationsPanel(settings); typeAnnotationsPanel})
    addTab(new ScalaArrangementPanel(settings))
    addTab(new CodeGenerationPanel(settings))
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

    if (scalaSettings.USE_SCALAFMT_FORMATTER) {
      shortenedPanel.exposeApply(settings)
    } else {
      super.apply(settings)
    }

    syncPanels(scalaSettings.USE_SCALAFMT_FORMATTER)
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

    syncPanels(scalaSettings.USE_SCALAFMT_FORMATTER)
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
        syncPanels(scalaSettings.USE_SCALAFMT_FORMATTER)
      }
    })

    import GridConstraints._
    val CAN_SHRINK_AND_GROW = SIZEPOLICY_CAN_SHRINK | SIZEPOLICY_CAN_GROW

    def constraint(row: Int, col: Int, fill: Int, HSizePolicy: Int, VSizePolicy: Int) =
      new GridConstraints(row, col, 1, 1, ANCHOR_CENTER, fill, HSizePolicy, VSizePolicy, null, null, null, 0, false)

    val formatterSelectorPanel = new JPanel(new GridLayoutManager(1, 3, new Insets(0, 10, 0, 0), -1, -1))
    formatterSelectorPanel.add(new JBLabel(ScalaBundle.message("scala.root.code.style.panel.formatter")), constraint(0, 0, FILL_BOTH, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
    formatterSelectorPanel.add(formatterSelectorComboBox, constraint(0, 1, FILL_HORIZONTAL, CAN_SHRINK_AND_GROW, SIZEPOLICY_FIXED))
    formatterSelectorPanel.add(new Spacer, constraint(0, 2, FILL_HORIZONTAL, SIZEPOLICY_WANT_GROW, SIZEPOLICY_CAN_SHRINK))

    outerPanel = new JPanel(new GridLayoutManager(7, 2, new Insets(0, 0, 0, 0), -1, -1))
    outerPanel.add(formatterSelectorPanel, constraint(0, 0, FILL_BOTH, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED))
    outerPanel.add(innerPanel, constraint(1, 0, FILL_BOTH, CAN_SHRINK_AND_GROW, CAN_SHRINK_AND_GROW))

    shortenedPanel = new ScalafmtTabbedLanguageCodeStylePanel(currentSettings, settings)
    outerPanel.add(shortenedPanel.getPanel, constraint(2, 0, FILL_BOTH, CAN_SHRINK_AND_GROW, CAN_SHRINK_AND_GROW))
    // this is required in order scrolling works correctly with small settings window height
    // otherwise on small screens the settings can be practically unusable
    innerPanel.setMinimumSize(new Dimension(1, 1))

    toggleSettingsVisibility(false)
  }

  def onProjectSet(project: Project): Unit = {
    shortenedPanel.onProjectSet(project)
    typeAnnotationsPanel.onProjectSet(project)
  }

  // scalaFmtSettingsPanel.setModel should be called in order that its settings are saved properly
  // setModel in TabbedLanguageCodeStylePanel is final so we can't override and have to use workaround method
  def onModelSet(model: CodeStyleSchemesModel): Unit = shortenedPanel.setModel(model)

  private def syncPanels(useExternalFormatter: Boolean): Unit = {
    val tempSettings = settings.clone(): @nowarn("cat=deprecation")
    if (useExternalFormatter) {
      shortenedPanel.exposeApply(tempSettings)
      // we need to invoke applySettingsToModel, which is done inside onSomethingChanged
      shortenedPanel.onSomethingChanged()
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
}

object ScalaTabbedCodeStylePanel {
  @NonNls
  private val formatters: Map[String, Int] = Map(
    "IntelliJ" -> ScalaCodeStyleSettings.INTELLIJ_FORMATTER,
    "Scalafmt" -> ScalaCodeStyleSettings.SCALAFMT_FORMATTER,
  )

  private class ScalafmtTabbedLanguageCodeStylePanel(currentSettings: CodeStyleSettings, settings: CodeStyleSettings)
    extends TabbedLanguageCodeStylePanel(ScalaLanguage.INSTANCE, currentSettings, settings) {

    private var scalaFmtSettingsPanel: ScalaFmtSettingsPanel = _

    override protected def initTabs(settings: CodeStyleSettings): Unit = {
      scalaFmtSettingsPanel = new ScalaFmtSettingsPanel(settings)
      addTab(scalaFmtSettingsPanel)
      addTab(new ImportsPanel(settings))
      addTab(new MultiLineStringCodeStylePanel(settings))
      addTab(new TypeAnnotationsPanel(settings))
      addTab(new ScalaArrangementPanel(settings))
      addTab(new CodeGenerationPanel(settings))
      val otherCodeStylePanel: OtherCodeStylePanel = new OtherCodeStylePanel(settings)
      addTab(otherCodeStylePanel)
      otherCodeStylePanel.toggleExternalFormatter(true)
    }

    def exposeIsModified(settings: CodeStyleSettings): Boolean = super.isModified(settings)
    def exposeApply(settings: CodeStyleSettings): Unit = super.apply(settings)
    def exposeResetImpl(settings: CodeStyleSettings): Unit = super.resetImpl(settings)

    def onProjectSet(project: Project): Unit = scalaFmtSettingsPanel.onProjectSet(project)
  }
}