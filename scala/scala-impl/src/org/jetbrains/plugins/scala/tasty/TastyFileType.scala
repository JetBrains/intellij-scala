package org.jetbrains.plugins.scala.tasty

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.ScalaFileType

import javax.swing.Icon

object TastyFileType extends FileType {
  override def getName: String = tastyName

  override def getDescription: String = tastyName

  override def getDefaultExtension: String = "tasty"

  //simply use the same icon which is used by ScalaFileType
  override def getIcon: Icon = ScalaFileType.INSTANCE.getIcon

  override def isBinary = true

  override def isReadOnly = true

  override def getCharset(file: VirtualFile, content: Array[Byte]): String = null
}