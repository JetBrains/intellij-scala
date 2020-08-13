package org.jetbrains.plugins.scala.worksheet.ui.dialog

import com.intellij.psi.PsiFile
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import javax.swing._
import java.awt._

import com.intellij.ui.components.JBTabbedPane
import org.jetbrains.plugins.scala.ScalaBundle

class WorksheetAllSettingsPanel(
  myFile: PsiFile,
  initialCurrentSettings: WorksheetSettingsData,
  initialDefaultSettings: WorksheetSettingsData
) extends JPanel {

  private val currentFileSettingsForm = new WorksheetSettingsSetForm(myFile, initialCurrentSettings)
  private val defaultSettingsForm     = new WorksheetSettingsSetForm(myFile.getProject, initialDefaultSettings)

  init()

  private def init(): Unit = {
    this.setLayout(new GridLayoutManager(1, 1, JBUI.emptyInsets, -1, -1))
    this.setPreferredSize(new Dimension(200, 200))

    val tabbedPane = new JBTabbedPane
    tabbedPane.addTab(ScalaBundle.message("worksheet.settings.panel.settings.for.module", myFile.getName), currentFileSettingsForm.getMainPanel)
    tabbedPane.addTab(ScalaBundle.message("worksheet.settings.panel.default.settings"), defaultSettingsForm.getMainPanel)

    val constraints = new GridConstraints
    constraints.setFill(GridConstraints.FILL_BOTH)
    this.add(tabbedPane, constraints)
  }

  def fileSettings: WorksheetSettingsData = currentFileSettingsForm.getFilledSettingsData
  def defaultFileSettings: WorksheetSettingsData = defaultSettingsForm.getFilledSettingsData
}