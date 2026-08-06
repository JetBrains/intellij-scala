package org.jetbrains.plugins.scala.worksheet.settings.ui

import java.awt._

import com.intellij.psi.PsiFile
import com.intellij.ui.components.JBTabbedPane
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager}
import com.intellij.util.ui.JBUI
import javax.swing._
import org.jetbrains.plugins.scala.worksheet.WorksheetBundle
import org.jetbrains.plugins.scala.worksheet.settings.ui.WorksheetSettingsPanel.TabTypeData

private final class WorksheetAllSettingsTabbedPanel(
  myFile: PsiFile,
  initialFileSettings: WorksheetSettingsData,
  initialDefaultSettings: WorksheetSettingsData,
  availableProfilesProvider: () => Seq[String]
) extends JPanel {

  private def project = myFile.getProject

  private val currentFileSettingsPanel = new WorksheetSettingsPanel(
    TabTypeData.FileSettingsTab(project, myFile.getVirtualFile),
    initialFileSettings,
    availableProfilesProvider
  )

  private val defaultSettingsPanelPanel = new WorksheetSettingsPanel(
    TabTypeData.DefaultProjectSettingsTab(project),
    initialDefaultSettings,
    availableProfilesProvider
  )

  locally {
    this.setLayout(new GridLayoutManager(1, 1, JBUI.emptyInsets, -1, -1))
    this.setPreferredSize(new Dimension(200, 200))

    val tabbedPane = new JBTabbedPane
    tabbedPane.addTab(WorksheetBundle.message("worksheet.settings.panel.settings.for.module", myFile.getName), currentFileSettingsPanel)
    tabbedPane.addTab(WorksheetBundle.message("worksheet.settings.panel.default.settings"), defaultSettingsPanelPanel)

    val constraints = new GridConstraints
    constraints.setFill(GridConstraints.FILL_BOTH)
    this.add(tabbedPane, constraints)
  }

  def fileSettingsData: WorksheetSettingsData = currentFileSettingsPanel.filledSettingsData
  def defaultProjectSettingsData: WorksheetSettingsData = defaultSettingsPanelPanel.filledSettingsData
}