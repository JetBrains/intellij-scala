package org.jetbrains.sbt
package project.settings

import java.awt.FlowLayout
import javax.swing._

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil._
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import org.jetbrains.annotations.NotNull

/**
 * @author Pavel Fatin
 */
class SbtProjectSettingsControl(context: Context, initialSettings: SbtProjectSettings)
        extends AbstractExternalProjectSettingsControl[SbtProjectSettings](initialSettings) {

  private val resolveClassifiersCheckBox = new JCheckBox(SbtBundle("sbt.settings.resolveClassifiers"))
  private val resolveJavadocsCheckBox = new JCheckBox(SbtBundle("sbt.settings.resolveJavadocs"))
  private val resolveSbtClassifiersCheckBox = new JCheckBox(SbtBundle("sbt.settings.resolveSbtClassifiers"))

  def fillExtraControls(@NotNull content: PaintAwarePanel, indentLevel: Int) {
    val downloadPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
    downloadPanel.add(resolveClassifiersCheckBox)
    downloadPanel.add(resolveJavadocsCheckBox)
    downloadPanel.add(resolveSbtClassifiersCheckBox)
    content.add(new JLabel("Download:"), getLabelConstraints(indentLevel))
    content.add(downloadPanel, getFillLineConstraints(indentLevel))
  }

  def isExtraSettingModified: Boolean = {
    val settings = getInitialSettings

    resolveClassifiersCheckBox.isSelected != settings.resolveClassifiers ||
    resolveSbtClassifiersCheckBox.isSelected != settings.resolveSbtClassifiers ||
    resolveJavadocsCheckBox.isSelected != settings.resolveJavadocs
  }

  protected def resetExtraSettings(isDefaultModuleCreation: Boolean) {
    val settings = getInitialSettings

    resolveClassifiersCheckBox.setSelected(settings.resolveClassifiers)
    resolveSbtClassifiersCheckBox.setSelected(settings.resolveSbtClassifiers)
    resolveJavadocsCheckBox.setSelected(settings.resolveJavadocs)
  }

  override def updateInitialExtraSettings() {
    applyExtraSettings(getInitialSettings)
  }

  protected def applyExtraSettings(settings: SbtProjectSettings) {
    settings.resolveClassifiers = resolveClassifiersCheckBox.isSelected
    settings.resolveSbtClassifiers = resolveSbtClassifiersCheckBox.isSelected
    settings.resolveJavadocs = resolveJavadocsCheckBox.isSelected
  }

  def validate(sbtProjectSettings: SbtProjectSettings): Boolean = true
}
