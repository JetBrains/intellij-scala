package org.jetbrains.plugins.scala.worksheet.settings

import com.intellij.ide.scratch.{ScratchFileService, ScratchRootType}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileWithId}
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.lang.psi.api.{FileDeclarationsHolder, ScalaFile}
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettingsProfile}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.ScalaUtil
import org.jetbrains.plugins.scala.worksheet.actions.RunWorksheetAction
import org.jetbrains.plugins.scala.worksheet.processor.{FileAttributeUtilCache, WorksheetPerFileConfig}
import org.jetbrains.plugins.scala.worksheet.server.InProcessServer

/**
  * User: Dmitry.Naydanov
  * Date: 14.03.18.
  */
class WorksheetFileSettings(file: PsiFile) extends WorksheetCommonSettings {
  import WorksheetFileSettings._
  
  override def project: Project = file.getProject

  private def getDefaultSettings: WorksheetCommonSettings = WorksheetCommonSettings.getInstance(project)

  private def getStringSetting(attr: FileAttribute, orDefault: => String): String =
    FileAttributeUtilCache.readAttribute(attr, file).getOrElse(orDefault)

  private def setStringSetting(attr: FileAttribute, value: String) {
    FileAttributeUtilCache.writeAttribute(attr, file, value)
  }

  private def getBooleanSetting(attribute: FileAttribute, orDefault: => Boolean): Boolean =
    FileAttributeUtilCache.readAttribute(attribute, file).map {
      case `enabled` => true
      case _ => false
    }.getOrElse(orDefault)

  private def setBooleanSetting(attribute: FileAttribute, value: Boolean) {
    setEnabled(file, attribute, value)
  }

  override def isRepl: Boolean = getBooleanSetting(IS_WORKSHEET_REPL_MODE, getDefaultSettings.isRepl)

  override def isInteractive: Boolean = getBooleanSetting(IS_AUTORUN, getDefaultSettings.isInteractive)

  override def isMakeBeforeRun: Boolean = getBooleanSetting(IS_MAKE_BEFORE_RUN, getDefaultSettings.isMakeBeforeRun)

  override def getModuleName: String = getStringSetting(CP_MODULE_NAME, getDefaultSettings.getModuleName)

  override def getCompilerProfileName: String = getStringSetting(COMPILER_PROFILE, getDefaultSettings.getCompilerProfileName)

  override def setRepl(value: Boolean): Unit = setBooleanSetting(IS_WORKSHEET_REPL_MODE, value)

  override def setInteractive(value: Boolean): Unit = setBooleanSetting(IS_AUTORUN, value)

  override def setMakeBeforeRun(value: Boolean): Unit = setBooleanSetting(IS_MAKE_BEFORE_RUN, value)

  override def setModuleName(value: String): Unit = setStringSetting(CP_MODULE_NAME, value)

  override def setCompilerProfileName(value: String): Unit = setStringSetting(COMPILER_PROFILE, value)

  override def getCompilerProfile: ScalaCompilerSettingsProfile = {
    val compilerConfiguration = ScalaCompilerConfiguration.instanceIn(project)

    if (!isScratchWorksheet(file)) {
      for {
        vFile <- ScalaUtil.findVirtualFile(file)
        module <- ScalaUtil.getModuleForFile(vFile, file.getProject)
        profile <- compilerConfiguration.customProfiles.find(_.getModuleNames.contains(module.getName))
      } return profile

      return compilerConfiguration.defaultProfile
    }

    val name = getCompilerProfileName

    compilerConfiguration.customProfiles.find(_.getName == name).getOrElse(compilerConfiguration.defaultProfile)
  }

  override def getModuleFor: Module = Option(super.getModuleFor).getOrElse(getModuleFor(file.getVirtualFile))

  private def getModuleFor(vFile: VirtualFile): Module = {
    vFile match {
      case _: VirtualFileWithId =>
        Option(ProjectFileIndex.SERVICE getInstance project getModuleForFile
          vFile) getOrElse project.anyScalaModule.map(_.module).orNull
      case _ => project.anyScalaModule.map(_.module).orNull
    }
  }
}

object WorksheetFileSettings extends WorksheetPerFileConfig {
  private val IS_MAKE_BEFORE_RUN = new FileAttribute("ScalaWorksheetMakeBeforeRun", 1, true)
  private val CP_MODULE_NAME = new FileAttribute("ScalaWorksheetModuleForCp", 1, false)
  private val IS_WORKSHEET_REPL_MODE = new FileAttribute("IsWorksheetReplMode", 1, true)
  private val COMPILER_PROFILE = new FileAttribute("ScalaWorksheetCompilerProfile", 1, false)
  private val IS_AUTORUN = new FileAttribute("ScalaWorksheetAutoRun", 1, true)


  def isReplLight(file: FileDeclarationsHolder): Boolean = {
    file match {
      case scalaFile: ScalaFile =>
        FileAttributeUtilCache.readAttributeLight(IS_WORKSHEET_REPL_MODE, scalaFile).contains("enabled")
      case _ => false
    }
  }

  def isRepl(file: PsiFile): Boolean = isEnabled(file, IS_WORKSHEET_REPL_MODE) && WorksheetProjectSettings.getRunType(file.getProject) == InProcessServer

  def shouldShowReplWarning(file: PsiFile): Boolean = isEnabled(file, IS_WORKSHEET_REPL_MODE) && WorksheetProjectSettings.getRunType(file.getProject) != InProcessServer

  def isScratchWorksheet(vFileOpt: Option[VirtualFile], project: Project): Boolean = vFileOpt.exists {
    vFile => ScratchFileService.getInstance().getRootType(vFile).isInstanceOf[ScratchRootType] &&
      ScalaProjectSettings.getInstance(project).isTreatScratchFilesAsWorksheet
  }

  def isScratchWorksheet(file: PsiFile): Boolean = isScratchWorksheet(Option(file.getVirtualFile), file.getProject)
}