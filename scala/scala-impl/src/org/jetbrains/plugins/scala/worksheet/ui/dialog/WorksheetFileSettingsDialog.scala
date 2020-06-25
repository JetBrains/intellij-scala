package org.jetbrains.plugins.scala.worksheet.ui.dialog

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiFile
import javax.swing.{JComponent, SwingConstants}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettingsProfile}
import org.jetbrains.plugins.scala.worksheet.settings.{WorksheetCommonSettings, WorksheetFileSettings, WorksheetProjectSettings}

class WorksheetFileSettingsDialog(worksheetFile: PsiFile) extends DialogWrapper(worksheetFile.getProject, true, true) {

  private val fileSettings = WorksheetFileSettings(worksheetFile)
  private val projectSettings = WorksheetProjectSettings(worksheetFile.getProject)
  private val myPanel = new WorksheetAllSettingsPanel(worksheetFile, getFileSettingsData, getDefaultSettingsData)
  
  setTitle(ScalaBundle.message("worksheet.settings.panel.title"))
  setButtonsAlignment(SwingConstants.CENTER)
  init()
  
  override def createCenterPanel(): JComponent = myPanel

  override def doOKAction(): Unit = {
    applyFileSettings(myPanel.fileSettings)
    applyDefaultSettings(myPanel.defaultFileSettings)
    DaemonCodeAnalyzer.getInstance(worksheetFile.getProject).restart(worksheetFile)
    super.doOKAction()
  }
  
  private def getSettingsData(settings: WorksheetCommonSettings): WorksheetSettingsData = {
    val (selectedProfile, profiles) = WorksheetFileSettingsDialog.createCompilerProfileOptions(settings)

    new WorksheetSettingsData(settings.isInteractive, settings.isMakeBeforeRun, settings.getRunType, null, selectedProfile, profiles)
  }
  
  private def applySettingsData(settingsData: WorksheetSettingsData, settings: WorksheetCommonSettings): Unit = {
    if (settings.isMakeBeforeRun != settingsData.isMakeBeforeRun) settings.setMakeBeforeRun(settingsData.isMakeBeforeRun)
    if (settings.getRunType != settingsData.runType) settings.setRunType(settingsData.runType)
    if (settings.isInteractive != settingsData.isInteractive) settings.setInteractive(settingsData.isInteractive)

    Option(settingsData.cpModule).map(_.getName)
      .filter(_ != settings.getModuleName)
      .foreach(settings.setModuleName)

    Option(settingsData.compilerProfile).map(_.getName)
      .filter(_ != settings.getCompilerProfileName)
      .foreach(settings.setCompilerProfileName)
  }
  
  private def getFileSettingsData: WorksheetSettingsData = getSettingsData(fileSettings)
  
  private def getDefaultSettingsData: WorksheetSettingsData = getSettingsData(projectSettings)
  
  private def applyFileSettings(settingsData: WorksheetSettingsData): Unit =
    applySettingsData(settingsData, fileSettings)
  
  private def applyDefaultSettings(settingsData: WorksheetSettingsData): Unit =
    applySettingsData(settingsData, projectSettings)
}

object WorksheetFileSettingsDialog {
  def createCompilerProfileOptions(settings: WorksheetCommonSettings): (ScalaCompilerSettingsProfile, Array[ScalaCompilerSettingsProfile]) = {
    val config = ScalaCompilerConfiguration.instanceIn(settings.project)
    val defaultProfile = config.defaultProfile
    val profiles = Seq(defaultProfile) ++ config.customProfiles
    val profileName = settings.getCompilerProfileName

    (profiles.find(_.getName == profileName).getOrElse(defaultProfile), profiles.toArray)
  }
}