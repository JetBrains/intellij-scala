package org.jetbrains.plugins.scala.worksheet.ui.dialog

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiFile
import javax.swing.{JComponent, SwingConstants}
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettingsProfile}
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetCommonSettings

/**
  * User: Dmitry.Naydanov
  * Date: 05.02.18.
  */
class WorksheetFileSettingsDialog(worksheetFile: PsiFile) extends DialogWrapper(worksheetFile.getProject, true, true) {
  private val (fileSettings, projectSettings) = (WorksheetCommonSettings getInstance worksheetFile, WorksheetCommonSettings getInstance worksheetFile.getProject)
  private val myPanel = new WorksheetAllSettingsForm(worksheetFile, getFileSettingsData, getDefaultSettingsData)
  
  setTitle("Worksheet Settings")
  setButtonsAlignment(SwingConstants.CENTER)
  init()
  
  override def createCenterPanel(): JComponent = myPanel.getMainPanel

  override def doOKAction(): Unit = {
    applyFileSettings(myPanel.getFileSettings)
    applyDefaultSettings(myPanel.getDefaultSettings)
    super.doOKAction()
  }
  
  private def getSettingsData(settings: WorksheetCommonSettings): WorksheetSettingsData = {
    val (selectedProfile, profiles) = WorksheetFileSettingsDialog.createCompilerProfileOptions(settings)

    new WorksheetSettingsData(
      settings.isRepl,
      settings.isInteractive,
      settings.isMakeBeforeRun,
      null,
      selectedProfile,
      profiles
    )
  }
  
  private def applySettingsData(settingsData: WorksheetSettingsData, settings: WorksheetCommonSettings): Unit = {
    if (settings.isMakeBeforeRun != settingsData.isMakeBeforeRun) settings.setMakeBeforeRun(settingsData.isMakeBeforeRun)
    if (settings.isRepl != settingsData.isRepl) settings.setRepl(settingsData.isRepl)
    if (settings.isInteractive != settingsData.isInteractive) settings.setInteractive(settingsData.isInteractive)
    
    if (settingsData.cpModule != null && settingsData.cpModule.getName != settings.getModuleName) 
      settings.setModuleName(settingsData.cpModule.getName)
    if (settingsData.compilerProfile != null && settingsData.compilerProfile.getName != settings.getCompilerProfileName) 
      settings.setCompilerProfileName(settingsData.compilerProfile.getName)
  }
  
  private def getFileSettingsData: WorksheetSettingsData = getSettingsData(fileSettings)
  
  private def getDefaultSettingsData: WorksheetSettingsData = getSettingsData(projectSettings)
  
  private def applyFileSettings(settingsData: WorksheetSettingsData): Unit = {
    applySettingsData(settingsData, fileSettings)
  }
  
  private def applyDefaultSettings(settingsData: WorksheetSettingsData): Unit = {
    applySettingsData(settingsData, projectSettings)
  }
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