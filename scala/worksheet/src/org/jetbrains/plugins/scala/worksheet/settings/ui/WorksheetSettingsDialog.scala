package org.jetbrains.plugins.scala.worksheet.settings.ui

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import javax.swing.{JComponent, SwingConstants}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettingsProfile}
import org.jetbrains.plugins.scala.worksheet.WorksheetBundle
import org.jetbrains.plugins.scala.worksheet.actions.WorksheetFileHook
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings
import org.jetbrains.plugins.scala.worksheet.settings.persistent.WorksheetSettingsUpdater.WorksheetSettingsDelegateUpdater
import org.jetbrains.plugins.scala.worksheet.settings.persistent.{WorksheetFilePersistentSettings, WorksheetProjectDefaultPersistentSettings, WorksheetSettingsUpdater}

final class WorksheetSettingsDialog(worksheetFile: PsiFile)
  extends DialogWrapper(worksheetFile.getProject, true, true) {

  private def project: Project = worksheetFile.getProject
  private def virtualFile: VirtualFile = worksheetFile.getVirtualFile

  private val fileSettings              = WorksheetFileSettings(project, virtualFile)
  private val filePersistentSettings    = WorksheetFilePersistentSettings(virtualFile)
  private val projectPersistentSettings = WorksheetProjectDefaultPersistentSettings(project)

  private val myPanel = new WorksheetAllSettingsTabbedPanel(
    worksheetFile,
    getFileSettingsData,
    getProjectDefaultSettingsData,
    () => listOfProfiles
  )

  locally {
    setTitle(WorksheetBundle.message("worksheet.settings.panel.title"))
    init()
  }

  override def createCenterPanel(): JComponent = myPanel

  override def doOKAction(): Unit = {
    applyFileSettings(myPanel.fileSettingsData)
    applyDefaultSettings(myPanel.defaultProjectSettingsData)
    DaemonCodeAnalyzer.getInstance(project).restart(worksheetFile)
    super.doOKAction()
  }

  private def listOfProfiles: Seq[String] =
    ScalaCompilerConfiguration(project).allProfiles.map(_.getName)

  private def getFileSettingsData: WorksheetSettingsData =
    WorksheetSettingsData(
      fileSettings.isInteractive,
      fileSettings.isMakeBeforeRun,
      fileSettings.getRunType,
      fileSettings.getModule.orNull,
      fileSettings.getCompilerProfileName
    )

  private def getProjectDefaultSettingsData: WorksheetSettingsData = {
    val module: Option[Module] = {
      val moduleName = projectPersistentSettings.getModuleName
      moduleName.flatMap(ModuleManager.getInstance(project).findModuleByName(_).toOption)
    }
    /** can be empty in rare cases when project configuration changed, and profile was removed or renamed */
    val profile: Option[ScalaCompilerSettingsProfile] = {
      val profileName = projectPersistentSettings.getCompilerProfileName
      profileName.flatMap(ScalaCompilerConfiguration(project).findByProfileName)
    }
    WorksheetSettingsData(
      projectPersistentSettings.isInteractive,
      projectPersistentSettings.isMakeBeforeRun,
      projectPersistentSettings.getRunType,
      module.orNull,
      profile.map(_.getName).orNull
    )
  }

  private def applyFileSettings(settingsData: WorksheetSettingsData): Unit = {
    val fileSettingsUpdater = new WorksheetSettingsDelegateUpdater(filePersistentSettings) {
      // assuming that module can only be changed in scratch file worksheets
      override def setModuleName(value: String): Unit = {
        super.setModuleName(value)
        WorksheetFileHook.moduleUpdated(project, virtualFile)
      }
      override def setCompilerProfileName(value: String): Unit = {
        super.setCompilerProfileName(value)
        WorksheetFileHook.profileUpdated(project, virtualFile)
      }
    }
    applySettingsData(settingsData, getFileSettingsData, fileSettingsUpdater)
  }

  private def applyDefaultSettings(settingsData: WorksheetSettingsData): Unit =
    applySettingsData(settingsData, getProjectDefaultSettingsData, projectPersistentSettings)

  private def applySettingsData(
    filled: WorksheetSettingsData,
    current: WorksheetSettingsData,
    updater: WorksheetSettingsUpdater,
  ): Unit = {
    if (current.runType != filled.runType)
      updater.setRunType(filled.runType)
    if (current.isMakeBeforeRun != filled.isMakeBeforeRun)
      updater.setMakeBeforeRun(filled.isMakeBeforeRun)
    if (current.isInteractive != filled.isInteractive)
      updater.setInteractive(filled.isInteractive)
    if (current.cpModule != filled.cpModule)
      updater.setModuleName(Option(filled.cpModule).map(_.getName).orNull)
    if (current.compilerProfile != filled.compilerProfile)
      updater.setCompilerProfileName(filled.compilerProfile)
  }
}