package org.jetbrains.plugins.scala.worksheet.settings.ui

import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.ui.ConfigurationModuleSelector
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBCheckBox
import javax.swing._
import net.miginfocom.layout.CC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.util.ui.TextWithMnemonic.AbstractButtonExt
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType
import org.jetbrains.plugins.scala.worksheet.settings.ui.WorksheetSettingsPanel.TabTypeData
import org.jetbrains.plugins.scala.worksheet.settings.ui.WorksheetSettingsPanel.TabTypeData._
import org.jetbrains.plugins.scala.worksheet.{WorksheetBundle, WorksheetUtils}

private final class WorksheetSettingsPanel(
  tabTypeData: TabTypeData,
  settingsData: WorksheetSettingsData,
  availableProfilesProvider: () => Seq[String]
) extends JPanel {

  private val interactiveModeCheckBox      = new JBCheckBox
  private val makeProjectBeforeRunCheckBox = new JBCheckBox
  private val moduleComboBox               = new ModulesComboBox
  private val compilerProfileComboBox      = new ComboBox[String]
  private val runTypeComboBox              = new ComboBox[WorksheetExternalRunType]

  private val openCompilerProfileSettingsButton = new ShowCompilerProfileSettingsButton(
    selectedProfile _,
    () => updateProfiles(selectedProfile, listOfProfiles)
  ).getActionButton

  locally {
    initLayout()
    initData(settingsData)
  }

  def filledSettingsData: WorksheetSettingsData =
    WorksheetSettingsData(
      interactiveModeCheckBox.isSelected,
      makeProjectBeforeRunCheckBox.isSelected,
      runTypeComboBox.getItem,
      moduleComboBox.getSelectedModule,
      compilerProfileComboBox.getItem
    )

  private def selectedProfile: String =
    filledSettingsData.compilerProfile

  private def project: Project =
    tabTypeData.project

  private def listOfProfiles: Seq[String] =
    availableProfilesProvider()

  private def initData(settingsData: WorksheetSettingsData): Unit = {
    moduleComboBox.fillModules(project)
    // NOTE: this allows the selection to be empty only after combo box initialization
    // FIXME: Currently you can't unselect selected module, see: SCL-18054, IDEA-239791
    if (tabTypeData.is[DefaultProjectSettingsTab]) {
      moduleComboBox.allowEmptySelection(ConfigurationModuleSelector.NO_MODULE_TEXT)
    }

    val module = Option(settingsData.cpModule)
    module.foreach(moduleComboBox.setSelectedModule)

    val allowChangingModule = tabTypeData match {
      case DefaultProjectSettingsTab(_)          => true
      case FileSettingsTab(project, virtualFile) => WorksheetUtils.isScratchWorksheet(project, virtualFile)
    }
    moduleComboBox.setEnabled(allowChangingModule)

    tabTypeData match {
      case DefaultProjectSettingsTab(_) =>
        val note = WorksheetBundle.message("worksheet.settings.panel.default.setting.is.only.used.in.scratch.files.note")
        moduleComboBox.setToolTipText(note)

      case FileSettingsTab(_, _) =>
        if (!allowChangingModule) {
          val note = WorksheetBundle.message("worksheet.settings.panel.setting.can.be.changed.in.scratch.files.note")
          moduleComboBox.setToolTipText(note)
        }
    }

    runTypeComboBox.setModel(new DefaultComboBoxModel(WorksheetExternalRunType.getAllRunTypes))
    runTypeComboBox.setSelectedItem(settingsData.runType)
    interactiveModeCheckBox.setSelected(settingsData.isInteractive)
    makeProjectBeforeRunCheckBox.setSelected(settingsData.isMakeBeforeRun)

    // setup custom renderer to allow showing unselected (null) item
    compilerProfileComboBox.setRenderer(new NullableListCellRenderer(WorksheetBundle.message("worksheet.settings.panel.no.profile.selected")))
    if (allowDeselectingProfile)
      compilerProfileComboBox.setToolTipText(WorksheetBundle.message("worksheet.settings.panel.compiler.profile.of.worksheet.module.will.be.used.note"))
    updateProfiles(settingsData.compilerProfile, listOfProfiles)
  }

  private def updateProfiles(selectedProfile: String, profiles: Seq[String]): Unit = {
    compilerProfileComboBox.setSelectedItem(null)
    val modelItems = if (allowDeselectingProfile) null +: profiles else profiles
    compilerProfileComboBox.setModel(new DefaultComboBoxModel[String](modelItems.toArray))
    compilerProfileComboBox.setSelectedItem(selectedProfile)
  }

  private def allowDeselectingProfile: Boolean =
    tabTypeData.is[TabTypeData.DefaultProjectSettingsTab]

  private class NullableListCellRenderer(emptySelectionText: String) extends com.intellij.ui.SimpleListCellRenderer[String] {
    override def customize(list: JList[_ <: String], value: String, index: Int, selected: Boolean, hasFocus: Boolean): Unit = {
      val displayValue = if (value == null) emptySelectionText else value
      setText(displayValue)
    }
  }

  private def wrap = new CC().wrap()
  private def c = new CC()

  private def initLayout(): Unit = {
    val root = this
    root.setLayout(new MigLayout)

    interactiveModeCheckBox.setTextWithMnemonic(WorksheetBundle.message("worksheet.settings.panel.interactive.mode"))
    root.add(interactiveModeCheckBox, wrap)

    makeProjectBeforeRunCheckBox.setTextWithMnemonic(WorksheetBundle.message("worksheet.settings.panel.change.make.button"))
    root.add(makeProjectBeforeRunCheckBox, wrap)

    root.add(new JLabel(WorksheetBundle.message("worksheet.settings.panel.run.type")))
    root.add(runTypeComboBox, wrap.growX())

    root.add(new JLabel(WorksheetBundle.message("worksheet.settings.panel.use.class.path.of.module")))
    root.add(moduleComboBox, c.wrap().growX())

    root.add(new JLabel(WorksheetBundle.message("worksheet.settings.panel.compiler.profile")))
    root.add(compilerProfileComboBox, c.growX())
    root.add(openCompilerProfileSettingsButton)
  }
}

object WorksheetSettingsPanel {

  sealed abstract class TabTypeData {
    def project: Project
  }
  object TabTypeData {
    final case class DefaultProjectSettingsTab(override val project: Project) extends TabTypeData
    final case class FileSettingsTab(override val project: Project, virtualFile: VirtualFile) extends TabTypeData
  }

  case class UiData(profiles: Seq[String])
}