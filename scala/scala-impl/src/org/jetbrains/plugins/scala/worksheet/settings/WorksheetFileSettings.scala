package org.jetbrains.plugins.scala
package worksheet
package settings

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileWithId}
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.{FileDeclarationsHolder, ScFile, ScalaFile}
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettingsProfile}
import org.jetbrains.plugins.scala.util.ScalaUtil
import org.jetbrains.plugins.scala.worksheet.processor.{FileAttributeUtilCache, WorksheetPerFileConfig}

import scala.ref.WeakReference

/**
  * User: Dmitry.Naydanov
  * Date: 14.03.18.
  */
class WorksheetFileSettings(file: PsiFile) extends WorksheetCommonSettings {

  import WorksheetFileSettings.SerializableWorksheetAttributes._
  import WorksheetFileSettings.SerializableWorksheetAttributes.SerializableInFileAttribute
  import WorksheetFileSettings._

  override def project: Project = file.getProject

  private def getDefaultSettings = WorksheetProjectSettings(project)

  private def getSetting[T](attr: FileAttribute, orDefault: => T)
                           (implicit ev: SerializableInFileAttribute[T]): T =
    WorksheetFileSettings.getSetting(file.getVirtualFile, attr, orDefault)

  private def setSetting[T](attr: FileAttribute, value: T)
                           (implicit ev: SerializableInFileAttribute[T]): Unit =
    WorksheetFileSettings.setSetting(file.getVirtualFile, attr, value)

  override def getRunType: WorksheetExternalRunType = getSetting(WORKSHEET_RUN_TYPE, getDefaultSettings.getRunType)

  // TODO: not this method should be optional but WorksheetFileSettings.apply
  def getRunTypeOpt: Option[WorksheetExternalRunType] = Option(getSetting(WORKSHEET_RUN_TYPE, null: WorksheetExternalRunType))

  override def setRunType(runType: WorksheetExternalRunType): Unit = setSetting(WORKSHEET_RUN_TYPE, runType)

  override def isInteractive: Boolean = getSetting(IS_AUTORUN, getDefaultSettings.isInteractive)

  override def isMakeBeforeRun: Boolean = getSetting(IS_MAKE_BEFORE_RUN, getDefaultSettings.isMakeBeforeRun)

  override def getModuleName: String = getSetting(CP_MODULE_NAME, getDefaultSettings.getModuleName)

  override def getCompilerProfileName: String = getSetting(COMPILER_PROFILE, getDefaultSettings.getCompilerProfileName)

  override def setInteractive(value: Boolean): Unit = setSetting(IS_AUTORUN, value)

  override def setMakeBeforeRun(value: Boolean): Unit = setSetting(IS_MAKE_BEFORE_RUN, value)

  override def setCompilerProfileName(value: String): Unit = setSetting(COMPILER_PROFILE, value)

  override def getCompilerProfile: ScalaCompilerSettingsProfile = {
    implicit val fileProject: Project = file.getProject
    val configuration = ScalaCompilerConfiguration.instanceIn(project)

    val customProfiles = configuration.customProfiles
    val maybeCustomProfile = if (isScratchWorksheet(file.getVirtualFile)) {
      val name = getCompilerProfileName
      customProfiles.find(_.getName == name)
    } else {
      for {
        ScFile.VirtualFile(virtualFile) <- Some(file)
        module <- ScalaUtil.getModuleForFile(virtualFile)
        profile <- customProfiles.find(_.moduleNames.contains(module.getName))
      } yield profile
    }
    maybeCustomProfile.getOrElse(configuration.defaultProfile)
  }

  override def setModuleName(value: String): Unit = {
    setSetting(CP_MODULE_NAME, value)
    for {
      module <- Option(getModuleFor)
    } file.putUserData(UserDataKeys.SCALA_ATTACHED_MODULE, new WeakReference(module))
    DaemonCodeAnalyzerEx.getInstanceEx(project).restart(file)
  }

  override def getModuleFor: Module = super.getModuleFor match {
    case null =>
      val module = file.getVirtualFile match {
        case virtualFile: VirtualFileWithId =>
          val fileIndex = ProjectFileIndex.SERVICE.getInstance(project)
          Option(fileIndex.getModuleForFile(virtualFile))
        case _ =>
          None
      }
      module.orElse(project.anyScalaModule).orNull
    case module => module
  }
}

object WorksheetFileSettings extends WorksheetPerFileConfig {

  private val IS_MAKE_BEFORE_RUN = new FileAttribute("ScalaWorksheetMakeBeforeRun", 1, true)
  private val CP_MODULE_NAME = new FileAttribute("ScalaWorksheetModuleForCp", 1, false)
  private val COMPILER_PROFILE = new FileAttribute("ScalaWorksheetCompilerProfile", 1, false)
  private val IS_AUTORUN = new FileAttribute("ScalaWorksheetAutoRun", 1, true)
  private val WORKSHEET_RUN_TYPE = new FileAttribute("ScalaWorksheetRunType", 1, false)

  def apply(file: PsiFile): WorksheetFileSettings = new WorksheetFileSettings(file)

  def isReplLight(file: FileDeclarationsHolder): Boolean = {
    file match {
      case scalaFile: ScalaFile =>
        val attrValue = FileAttributeUtilCache.readAttributeLight(WORKSHEET_RUN_TYPE, scalaFile)
        attrValue.flatMap(WorksheetExternalRunType.findRunTypeByName).exists(_.isReplRunType)
      case _ => false
    }
  }

  def isRepl(file: PsiFile): Boolean = getRunType(file).isReplRunType

  def getRunType(file: PsiFile): WorksheetExternalRunType = new WorksheetFileSettings(file).getRunType

  def isScratchWorksheet(file: VirtualFile)
                        (implicit project: Project): Boolean = {
    WorksheetFileType.hasScratchRootType(file) &&
      WorksheetFileType.treatScratchFileAsWorksheet
  }

  private object SerializableWorksheetAttributes {
    trait SerializableInFileAttribute[T] {
      def readAttribute(attr: FileAttribute, file: VirtualFile): Option[T] =
        FileAttributeUtilCache.readAttribute(attr, file).map(convertTo)

      def writeAttribute(attr: FileAttribute, file: VirtualFile, t: T): Unit =
        FileAttributeUtilCache.writeAttribute(attr, file, convertFrom(t))

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

      override def convertTo(s: String): WorksheetExternalRunType = WorksheetExternalRunType.findRunTypeByName(s).getOrElse(WorksheetExternalRunType.getDefaultRunType)
    }
  }

  import SerializableWorksheetAttributes.SerializableInFileAttribute

  private def getSetting[T](vFile: VirtualFile, attr: FileAttribute, orDefault: => T)
                           (implicit ev: SerializableInFileAttribute[T]): T =
    ev.readAttribute(attr, vFile).getOrElse(orDefault)

  private def setSetting[T](vFile: VirtualFile, attr: FileAttribute, value: T)
                           (implicit ev: SerializableInFileAttribute[T]): Unit =
    ev.writeAttribute(attr, vFile, value)

  import SerializableWorksheetAttributes.StringFileAttribute

  def getModuleName(file: VirtualFile): Option[String] =
    Option(getSetting[String](file, CP_MODULE_NAME, null))
}