package org.jetbrains.plugins.scala

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.{LanguageFileType, ex}
import com.intellij.openapi.vfs.VirtualFile

abstract class LanguageFileTypeBase(language: Language) extends LanguageFileType(language)
  with ex.FileTypeIdentifiableByVirtualFile {

  override def getName: String = getLanguage.getID

  override def getDefaultExtension: String = getName.toLowerCase

  override def getDescription: String = getName + " files"

  // TODO Temporary fix for Upsource
  // (FileTypeManagerImpl.isFileOfType is called from ScalaSourceFilterScope,
  //   while FileTypeManager contains no .scala pattern)
  override final def isMyFileType(file: VirtualFile): Boolean =
    isMyFileExtension(file)

  protected def isMyFileExtension(file: VirtualFile): Boolean =
    getDefaultExtension == file.getExtension
}

