package org.jetbrains.sbt
package language

import com.intellij.openapi.fileTypes.{LanguageFileType, ex}
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.ScalaLanguage

/**
 * @author Pavel Fatin
 */
//noinspection TypeAnnotation
object SbtFileType extends LanguageFileType(ScalaLanguage.INSTANCE)
  with ex.FileTypeIdentifiableByVirtualFile {

  override def getName: String = Sbt.Name

  override def getDescription = s"$getName files"

  override def getDefaultExtension = getName

  override def getIcon = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/sbt_file.svg")

  override def isMyFileType(file: VirtualFile): Boolean =
    getDefaultExtension == file.getExtension
}
