package org.jetbrains.plugins.scala.worksheet.ui.dialog

import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.ui.ConfigurationModuleSelector
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.psi.PsiFile
import com.intellij.ui.components.JBCheckBox
import javax.swing._
import net.miginfocom.layout.CC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettingsProfile
import org.jetbrains.plugins.scala.util.ui.TextWithMnemonic.AbstractButtonExt
import org.jetbrains.plugins.scala.worksheet.settings.{WorksheetExternalRunType, WorksheetFileSettings, WorksheetProjectSettings}
import org.jetbrains.plugins.scala.worksheet.{WorksheetBundle, WorksheetUtils}

final class WorksheetSettingsSetForm private(
  val myProject: Project,
  val myFile: PsiFile,
  val settingsData: WorksheetSettingsData
) {
  private val mainPanel: JPanel = new JPanel

  private val interactiveModeCheckBox           = new JBCheckBox
  private val makeProjectBeforeRunCheckBox      = new JBCheckBox
  private val moduleComboBox                    = new ModulesComboBox
  private val compilerProfileComboBox           = new ComboBox[ScalaCompilerSettingsProfile]
  private val openCompilerProfileSettingsButton = new ShowCompilerProfileSettingsButton(this).getActionButton
  private val runTypeComboBox                   = new ComboBox[WorksheetExternalRunType]

  locally {
    initLayout()
    initData(settingsData)
  }

  def this(file: PsiFile, settingsData: WorksheetSettingsData) =
    this(file.getProject, file, settingsData)

  def this(project: Project, settingsData: WorksheetSettingsData) =
    this(project, null, settingsData)

  def getMainPanel: JPanel = mainPanel

  def getFile: PsiFile = myFile

  def getRunType: WorksheetExternalRunType = runTypeComboBox.getItem

  def onProfilesReload(compilerProfile: ScalaCompilerSettingsProfile, profiles: Array[ScalaCompilerSettingsProfile]): Unit = {
    compilerProfileComboBox.setSelectedItem(null)
    compilerProfileComboBox.setModel(new DefaultComboBoxModel[ScalaCompilerSettingsProfile](profiles))
    compilerProfileComboBox.setSelectedItem(compilerProfile)
  }

  def getFilledSettingsData: WorksheetSettingsData =
    WorksheetSettingsData(
      interactiveModeCheckBox.isSelected,
      makeProjectBeforeRunCheckBox.isSelected,
      runTypeComboBox.getItem,
      if (moduleComboBox.isEnabled) moduleComboBox.getSelectedModule else null,
      compilerProfileComboBox.getItem,
      null
    )

  def getSelectedProfileName: String = {
    val selectedProfile = compilerProfileComboBox.getItem
    if (selectedProfile == null) null
    else selectedProfile.getName
  }

  private def initData(settingsData: WorksheetSettingsData): Unit = {
    moduleComboBox.fillModules(myProject)
    // NOTE: this allows the selection to be empty only after combo box initialization
    // You can't unselect selected module, see:
    // TODO: SCL-18054, IDEA-239791
    moduleComboBox.allowEmptySelection(ConfigurationModuleSelector.NO_MODULE_TEXT)

    val isDefaultSettings = myFile == null
    val tooltip = WorksheetBundle.message("worksheet.settings.panel.using.class.path.of.the.module")
    if (isDefaultSettings) {
      val note = WorksheetBundle.message("worksheet.settings.panel.using.class.path.of.the.module.for.default.settings.note")
      //noinspection HardCodedStringLiteral (using html tag)
      moduleComboBox.setToolTipText(tooltip + "<br>" + note)
    }
    else
      moduleComboBox.setToolTipText(tooltip)

    val settings =
      if (!isDefaultSettings) WorksheetFileSettings.apply(myFile)
      else WorksheetProjectSettings.apply(myProject)

    val defaultModule = settings.getModuleFor
    if (defaultModule != null) {
      moduleComboBox.setSelectedModule(defaultModule)
      val enabled = isDefaultSettings || WorksheetUtils.isScratchWorksheet(myProject, myFile.getVirtualFile)
      moduleComboBox.setEnabled(enabled)
    }

    runTypeComboBox.setModel(new DefaultComboBoxModel(WorksheetExternalRunType.getAllRunTypes))
    runTypeComboBox.setSelectedItem(settingsData.runType)
    interactiveModeCheckBox.setSelected(settingsData.isInteractive)
    makeProjectBeforeRunCheckBox.setSelected(settingsData.isMakeBeforeRun)
    compilerProfileComboBox.setModel(new DefaultComboBoxModel(settingsData.profiles))
    compilerProfileComboBox.setSelectedItem(settingsData.compilerProfile)
  }

  private def wrap = new CC().wrap()
  private def c = new CC()

  private def initLayout(): Unit = {
    mainPanel.setLayout(new MigLayout)

    interactiveModeCheckBox.setTextWithMnemonic(WorksheetBundle.message("worksheet.settings.panel.interactive.mode"))
    mainPanel.add(interactiveModeCheckBox, wrap)

    makeProjectBeforeRunCheckBox.setTextWithMnemonic(WorksheetBundle.message("worksheet.settings.panel.change.make.button"))
    mainPanel.add(makeProjectBeforeRunCheckBox, wrap)

    mainPanel.add(new JLabel(WorksheetBundle.message("worksheet.settings.panel.run.type")))
    mainPanel.add(runTypeComboBox, wrap.growX())

    mainPanel.add(new JLabel(WorksheetBundle.message("worksheet.settings.panel.use.class.path.of.module")))
    mainPanel.add(moduleComboBox, wrap.growX())

    mainPanel.add(new JLabel(WorksheetBundle.message("worksheet.settings.panel.compiler.profile")))
    mainPanel.add(compilerProfileComboBox, c.growX())
    mainPanel.add(openCompilerProfileSettingsButton)
  }
}