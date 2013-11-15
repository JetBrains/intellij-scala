package org.jetbrains.sbt
package language

import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.plugins.scala.ScalaFileType

/**
 * @author Pavel Fatin
 */
object SbtFileType extends LanguageFileType(ScalaFileType.SCALA_LANGUAGE) {
  def getName = Sbt.Name

  def getDescription = Sbt.FileDescription

  def getDefaultExtension = Sbt.FileExtension

  def getIcon = Sbt.FileIcon
}
