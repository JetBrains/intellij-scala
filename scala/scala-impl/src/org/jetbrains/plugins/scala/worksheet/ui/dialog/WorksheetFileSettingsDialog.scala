package org.jetbrains.plugins.scala.worksheet.ui.dialog

import javax.swing.{JComponent, SwingConstants}

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettingsProfile}
import org.jetbrains.plugins.scala.worksheet.interactive.WorksheetAutoRunner
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler

/**
  * User: Dmitry.Naydanov
  * Date: 05.02.18.
  */
class WorksheetFileSettingsDialog(worksheetFile: PsiFile) extends DialogWrapper(worksheetFile.getProject, true, true) {
  private val myPanel = new WorksheetFileSettingsForm(worksheetFile, initFormSettings())
  setTitle(s"Settings for ${worksheetFile.getName}")
  setButtonsAlignment(SwingConstants.CENTER)
  init()
  
  override def createCenterPanel(): JComponent = myPanel.getMainPanel

  override def doOKAction(): Unit = {
    applySettings(myPanel.getSettings)
    super.doOKAction()
  }
  
  
  private def initFormSettings(): WorksheetSettingsData = {
    val (selectedProfile, profiles) = createCompilerProfileModel()
    
    new WorksheetSettingsData(
      WorksheetCompiler.isWorksheetReplMode(worksheetFile),
      WorksheetAutoRunner.isSetEnabled(worksheetFile),
      WorksheetCompiler.isMakeBeforeRun(worksheetFile),
      null,
      selectedProfile,
      profiles
    )
  }
  
  private def createCompilerProfileModel(): (ScalaCompilerSettingsProfile, Array[ScalaCompilerSettingsProfile]) = {
    val config = ScalaCompilerConfiguration.instanceIn(worksheetFile.getProject)
    val defaultProfile = config.defaultProfile
    val profiles = Seq(defaultProfile) ++ config.customProfiles
    
    val selected = WorksheetCompiler.getCustomCompilerProfileName(worksheetFile).flatMap {
      profileName => profiles.find(_.getName == profileName)
    }.getOrElse(defaultProfile)
    
    
    (selected, profiles.toArray)
  }
  
  private def applySettings(settingsData: WorksheetSettingsData) {
    if (settingsData.moduleName != null) {
      val nm = settingsData.moduleName
      val name = nm.getName
      
      if (!(name == WorksheetCompiler.getModuleForCpName(worksheetFile).orNull)) 
        WorksheetCompiler.setModuleForCpName(worksheetFile, name)
    }
    
    if (settingsData.compilerProfileName != null) 
      WorksheetCompiler.setCustomCompilerProfileName(worksheetFile, settingsData.compilerProfileName.getName)

    WorksheetCompiler.setMakeBeforeRun(worksheetFile, settingsData.isMakeBeforeRun)
    WorksheetCompiler.setWorksheetReplMode(worksheetFile, settingsData.isRepl)
    WorksheetAutoRunner.setAutorun(worksheetFile, settingsData.isInteractive)
  }
}
