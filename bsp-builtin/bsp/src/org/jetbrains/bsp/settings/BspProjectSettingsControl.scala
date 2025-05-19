package org.jetbrains.bsp.settings

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.service.ui.ExternalSystemJdkComboBoxUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil.getFillLineConstraints
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkComboBoxModel.createJdkComboBoxModel
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import org.jetbrains.annotations.Nullable
import org.jetbrains.bsp.BspBundle
import org.jetbrains.bsp.settings.BspProjectSettings.{AutoConfig, AutoPreImport, BspServerConfig, PreImportConfig}
import org.jetbrains.sbt.project.SdkResolutionUtil

import java.awt.BorderLayout
import javax.swing.{JCheckBox, JPanel}
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
  private val sdkComboBoxPanel: JPanel = new JPanel(new BorderLayout)
  private var sdkComboBox: Option[SdkComboBox] = None

  override def fillExtraControls(content: PaintAwarePanel, indentLevel: Int): Unit = {
    val fillLineConstraints = getFillLineConstraints(1)
    content.add(buildOnSaveCheckBox, fillLineConstraints)
    content.add(runPreImportTaskCheckBox, fillLineConstraints)
    content.add(sdkComboBoxPanel, fillLineConstraints)
  }

  override def isExtraSettingModified: Boolean = {
    val initial = getInitialSettings
    val isSdkComboBoxModified =
      sdkComboBox.exists(_.getModel.getSdksModel.isModified) || getSelectedJvmReference.exists(_ != initial.jdkReference)

    buildOnSaveCheckBox.isSelected != initial.buildOnSave ||
      runPreImportTaskCheckBox.isSelected != initial.runPreImportTask ||
      isSdkComboBoxModified
  }

  override def resetExtraSettings(isDefaultModuleCreation: Boolean): Unit = {
    val initial = getInitialSettings
    buildOnSaveCheckBox.setSelected(initial.buildOnSave)
    runPreImportTaskCheckBox.setSelected(initial.runPreImportTask)
    resetSdkComboBox(getProject, initial)
  }

  override def applyExtraSettings(settings: BspProjectSettings): Unit = {
    settings.buildOnSave = buildOnSaveCheckBox.isSelected
    settings.runPreImportTask = runPreImportTaskCheckBox.isSelected
    sdkComboBox.foreach { comboBox =>
      comboBox.getModel.getSdksModel.apply()
    }
    getSelectedJvmReference.foreach { reference =>
      settings.jdkReference = reference
    }
  }

  override def validate(settings: BspProjectSettings): Boolean = true

  override def updateInitialExtraSettings(): Unit = {
    applyExtraSettings(getInitialSettings)
  }

  private def getSelectedJvmReference: Option[String] =
    sdkComboBox.map { comboBox =>
      val provider = SdkResolutionUtil.getSdkLookupProvider(comboBox.getModel.getProject)
      val reference = ExternalSystemJdkComboBoxUtil.getSelectedJdkReference(comboBox, provider)
      reference
    }

  private def resetSdkComboBox(@Nullable project: Project, initial: BspProjectSettings): Unit = {
    val notNullProject = getNotNullProject(project)

    sdkComboBox.foreach { comboBox =>
      sdkComboBoxPanel.remove(comboBox)
    }

    val comboBoxModel = {
      val sdksModel = new ProjectSdksModel
      sdksModel.reset(notNullProject)
      createJdkComboBoxModel(notNullProject, sdksModel)
    }
    val comboBox = new SdkComboBox(comboBoxModel)

    sdkComboBox = Some(comboBox)
    sdkComboBoxPanel.add(comboBox, BorderLayout.CENTER)

    val provider = SdkResolutionUtil.getSdkLookupProvider(notNullProject)
    ExternalSystemJdkComboBoxUtil.setSelectedJdkReference(comboBox, provider, initial.jdkReference)
  }

  private def getNotNullProject(project: Project): Project =
    if (project != null) project
    else ProjectManager.getInstance.getDefaultProject
}