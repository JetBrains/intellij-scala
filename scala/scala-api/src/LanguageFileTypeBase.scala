package org.jetbrains.plugins.scala

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.{FileTypeManager, LanguageFileType}
import com.intellij.openapi.vfs.VirtualFile

abstract class LanguageFileTypeBase(language: Language) extends LanguageFileType(language) {

  //noinspection ReferencePassedToNls
  override def getName: String = getLanguage.getID

  override def getDefaultExtension: String = getName.toLowerCase

  //noinspection ScalaExtractStringToBundle,ReferencePassedToNls
  override def getDescription: String = getName + " files"

  // TODO Temporary fix for Upsource
  // (FileTypeManagerImpl.isFileOfType is called from ScalaSourceFilterScope,
  //   while FileTypeManager contains no .scala pattern)
  // take into account: SCL-16417
  // TODO maybe remove this method.
  //  It's a legacy of LanguageFileTypeBase inheriting from the FileTypeIdentifiableByVirtualFile
  private[jetbrains] def isMyFileType(file: VirtualFile): Boolean = {
    val ext = file.getExtension
    if (ext == null) return false
    FileTypeManager.getInstance.getFileTypeByExtension(ext) == this
  }
}

