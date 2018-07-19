package org.jetbrains.plugins.scala.worksheet.settings

import com.intellij.ide.scratch.{ScratchFileService, ScratchRootType}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileWithId}
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.{FileDeclarationsHolder, ScalaFile}
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettingsProfile}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.ScalaUtil
import org.jetbrains.plugins.scala.worksheet.processor.{FileAttributeUtilCache, WorksheetPerFileConfig}
import org.jetbrains.plugins.scala.worksheet.server.InProcessServer

/**
  * User: Dmitry.Naydanov
  * Date: 14.03.18.
  */
class WorksheetFileSettings(file: PsiFile) extends WorksheetCommonSettings {
  import WorksheetFileSettings._
  import WorksheetFileSettings.SerializableWorksheetAttributes._
  
  override def project: Project = file.getProject

  private def getDefaultSettings: WorksheetCommonSettings = WorksheetCommonSettings.getInstance(project)

  private def getSetting[T](attr: FileAttribute, orDefault: => T)(implicit ev: SerializableInFileAttribute[T]): T = 
    ev.readAttribute(attr, file).getOrElse(orDefault)
  
  private def setSetting[T](attr: FileAttribute, value: T)(implicit ev: SerializableInFileAttribute[T]): Unit =
    ev.writeAttribute(attr, file, value)

  override def getRunType: WorksheetExternalRunType = getSetting(WORKSHEET_RUN_TYPE, getDefaultSettings.getRunType)
  
  override def setRunType(runType: WorksheetExternalRunType): Unit = setSetting(WORKSHEET_RUN_TYPE, runType)

  override def isInteractive: Boolean = getSetting(IS_AUTORUN, getDefaultSettings.isInteractive)

  override def isMakeBeforeRun: Boolean = getSetting(IS_MAKE_BEFORE_RUN, getDefaultSettings.isMakeBeforeRun)

  override def getModuleName: String = getSetting(CP_MODULE_NAME, getDefaultSettings.getModuleName)

  override def getCompilerProfileName: String = getSetting(COMPILER_PROFILE, getDefaultSettings.getCompilerProfileName)

  override def setInteractive(value: Boolean): Unit = setSetting(IS_AUTORUN, value)

  override def setMakeBeforeRun(value: Boolean): Unit = setSetting(IS_MAKE_BEFORE_RUN, value)

  override def setModuleName(value: String): Unit = setSetting(CP_MODULE_NAME, value)

  override def setCompilerProfileName(value: String): Unit = setSetting(COMPILER_PROFILE, value)

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
  private val COMPILER_PROFILE = new FileAttribute("ScalaWorksheetCompilerProfile", 1, false)
  private val IS_AUTORUN = new FileAttribute("ScalaWorksheetAutoRun", 1, true)
  private val WORKSHEET_RUN_TYPE = new FileAttribute("ScalaWorksheetRunType", 1, false)

  def isReplLight(file: FileDeclarationsHolder): Boolean = {
    file match {
      case scalaFile: ScalaFile =>
        val attrValue = FileAttributeUtilCache.readAttributeLight(WORKSHEET_RUN_TYPE, scalaFile)
        attrValue.flatMap(RunTypes.findRunTypeByName).exists(_.isReplRunType)
      case _ => false
    }
  }

  def isRepl(file: PsiFile): Boolean = getRunType(file).isReplRunType

  def getRunType(file: PsiFile): WorksheetExternalRunType = new WorksheetFileSettings(file).getRunType
  
  def shouldShowReplWarning(file: PsiFile): Boolean = isRepl(file) && WorksheetProjectSettings.getMakeType(file.getProject) != InProcessServer

  def isScratchWorksheet(vFileOpt: Option[VirtualFile], project: Project): Boolean = vFileOpt.exists {
    vFile => ScratchFileService.getInstance().getRootType(vFile).isInstanceOf[ScratchRootType] &&
      ScalaProjectSettings.getInstance(project).isTreatScratchFilesAsWorksheet
  }

  def isScratchWorksheet(file: PsiFile): Boolean = isScratchWorksheet(Option(file.getVirtualFile), file.getProject)
  
  object SerializableWorksheetAttributes {
    trait SerializableInFileAttribute[T] {
      def readAttribute(attr: FileAttribute, file: PsiFile): Option[T] = {
        FileAttributeUtilCache.readAttribute(attr, file).map(convertTo)
      }

      def writeAttribute(attr: FileAttribute, file: PsiFile, t: T): Unit = {
        FileAttributeUtilCache.writeAttribute(attr, file, convertFrom(t))
      }

      def convertFrom(t: T): String
      def convertTo(s: String): T
    }

    implicit val StringFileAttribute: SerializableInFileAttribute[String] = new SerializableInFileAttribute[String] {
      override def convertFrom(t: String): String = t
      override def convertTo(s: String): String = s
    }

    implicit val BooleanFileAttribute: SerializableInFileAttribute[Boolean] with WorksheetPerFileConfig = new SerializableInFileAttribute[Boolean] with WorksheetPerFileConfig {
      override def convertFrom(t: Boolean): String = getStringRepresent(t)
      override def convertTo(s: String): Boolean = s match {
        case `enabled` => true
        case _ => false
      }
    }

    implicit val ExternalRunTypeAttribute: SerializableInFileAttribute[WorksheetExternalRunType] = new SerializableInFileAttribute[WorksheetExternalRunType] {
      override def convertFrom(t: WorksheetExternalRunType): String = t.getName

      override def convertTo(s: String): WorksheetExternalRunType = RunTypes.findRunTypeByName(s).getOrElse(RunTypes.getDefaultRunType)
    }
  }
}