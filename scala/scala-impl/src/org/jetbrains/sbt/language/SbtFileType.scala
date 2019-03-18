package org.jetbrains.sbt
package language

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.ScalaLanguage

/**
 * @author Pavel Fatin
 */
//noinspection TypeAnnotation
object SbtFileType extends LanguageFileType(ScalaLanguage.INSTANCE) {

  def getName: String = Sbt.Name

  def getDescription = s"$getName files"

  def getDefaultExtension = getName

  def getIcon = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/sbt_file.svg")

  def isSbtFile(file: VirtualFile): Boolean =
    file.getExtension == getDefaultExtension
}
