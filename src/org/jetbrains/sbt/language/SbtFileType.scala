package org.jetbrains.sbt
package language

import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.plugins.scala.ScalaLanguage

/**
 * @author Pavel Fatin
 */
object SbtFileType extends LanguageFileType(ScalaLanguage.INSTANCE) {
  def getName = Sbt.Name

  def getDescription = Sbt.FileDescription

  def getDefaultExtension = Sbt.FileExtension

  def getIcon = Sbt.FileIcon
}
