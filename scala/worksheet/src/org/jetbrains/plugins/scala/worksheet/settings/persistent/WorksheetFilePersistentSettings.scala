package org.jetbrains.plugins.scala.worksheet.settings.persistent

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.FileAttribute
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType
import org.jetbrains.plugins.scala.worksheet.settings.persistent.WorksheetFilePersistentSettings.FileAttributes

final class WorksheetFilePersistentSettings private(file: VirtualFile)
  extends WorksheetPersistentSettings {

  private def getSetting[T](attr: FileAttribute)
                           (implicit ev: SerializableInFileAttribute[T]): Option[T] =
    WorksheetFilePersistentSettings.getSetting(file, attr)

  private def setSetting[T](attr: FileAttribute, value: T)
                           (implicit ev: SerializableInFileAttribute[T]): Unit =
    WorksheetFilePersistentSettings.setSetting(file, attr, value)

  def isInteractive: Option[Boolean] = getSetting[Boolean](FileAttributes.IsAutorun)
  def isMakeBeforeRun: Option[Boolean] = getSetting[Boolean](FileAttributes.IsMakeBeforeRun)
  def getModuleName: Option[String] = getSetting[String](FileAttributes.CpModuleName)
  def getCompilerProfileName: Option[String] = getSetting[String](FileAttributes.CompilerProfile)
  def getRunType: Option[WorksheetExternalRunType] = getSetting[WorksheetExternalRunType](FileAttributes.RunType)

  override def setInteractive(value: Boolean): Unit = setSetting(FileAttributes.IsAutorun, value)
  override def setMakeBeforeRun(value: Boolean): Unit = setSetting(FileAttributes.IsMakeBeforeRun, value)
  override def setRunType(value: WorksheetExternalRunType): Unit = setSetting(FileAttributes.RunType, value)
  override def setCompilerProfileName(value: String): Unit = setSetting(FileAttributes.CompilerProfile, value)
  override def setModuleName(value: String): Unit = setSetting(FileAttributes.CpModuleName, value)
}

object WorksheetFilePersistentSettings {

  private[settings]
  trait WorksheetModuleChangedListener {
    def moduleChanged(file: VirtualFile, moduleName: String): Unit
  }

  def apply(file: VirtualFile): WorksheetFilePersistentSettings = new WorksheetFilePersistentSettings(file)

  object FileAttributes {
    val IsMakeBeforeRun = new FileAttribute("ScalaWorksheetMakeBeforeRun", 1, true)
    val CpModuleName    = new FileAttribute("ScalaWorksheetModuleForCp", 1, false)
    val CompilerProfile = new FileAttribute("ScalaWorksheetCompilerProfile", 1, false)
    val IsAutorun       = new FileAttribute("ScalaWorksheetAutoRun", 1, true)
    val RunType         = new FileAttribute("ScalaWorksheetRunType", 1, false)
  }

  private def getSetting[T](vFile: VirtualFile, attr: FileAttribute)
                           (implicit ev: SerializableInFileAttribute[T]): Option[T] =
    ev.readAttribute(attr, vFile)

  private def setSetting[T](vFile: VirtualFile, attr: FileAttribute, value: T)
                           (implicit ev: SerializableInFileAttribute[T]): Unit =
    ev.writeAttribute(attr, vFile, value)
}