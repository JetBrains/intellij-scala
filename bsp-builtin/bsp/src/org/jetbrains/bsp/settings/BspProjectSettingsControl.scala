package org.jetbrains.bsp.settings

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil.getFillLineConstraints
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import org.jetbrains.bsp.BspBundle
import org.jetbrains.bsp.settings.BspProjectSettings.{AutoConfig, AutoPreImport, BspServerConfig, PreImportConfig}

import javax.swing.JCheckBox
import scala.beans.BeanProperty

class BspProjectSettingsControl(settings: BspProjectSettings)
  extends AbstractExternalProjectSettingsControl[BspProjectSettings](null, settings) {

  @BeanProperty
  var buildOnSave = false

  @BeanProperty
  var runPreImportTask = true

  @BeanProperty
  var preImportConfig: PreImportConfig = AutoPreImport

  @BeanProperty
  var serverConfig: BspServerConfig = AutoConfig

  private val buildOnSaveCheckBox = new JCheckBox(BspBundle.message("bsp.protocol.build.automatically.on.file.save"))
  private val runPreImportTaskCheckBox = new JCheckBox(BspBundle.message("bsp.protocol.export.sbt.projects.to.bloop.before.import"))

  override def fillExtraControls(content: PaintAwarePanel, indentLevel: Int): Unit = {
    val fillLineConstraints = getFillLineConstraints(1)
    content.add(buildOnSaveCheckBox, fillLineConstraints)
    content.add(runPreImportTaskCheckBox, fillLineConstraints)
  }

  override def isExtraSettingModified: Boolean = {
    val initial = getInitialSettings
    buildOnSaveCheckBox.isSelected != initial.buildOnSave ||
      runPreImportTaskCheckBox.isSelected != initial.runPreImportTask
  }

  override def resetExtraSettings(isDefaultModuleCreation: Boolean): Unit = {
    val initial = getInitialSettings
    buildOnSaveCheckBox.setSelected(initial.buildOnSave)
    runPreImportTaskCheckBox.setSelected(initial.runPreImportTask)
  }

  override def applyExtraSettings(settings: BspProjectSettings): Unit = {
    settings.buildOnSave = buildOnSaveCheckBox.isSelected
    settings.runPreImportTask = runPreImportTaskCheckBox.isSelected
  }

  override def validate(settings: BspProjectSettings): Boolean = true

  override def updateInitialExtraSettings(): Unit = {
    applyExtraSettings(getInitialSettings)
  }

}