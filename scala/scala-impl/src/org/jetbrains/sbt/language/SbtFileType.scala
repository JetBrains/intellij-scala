package org.jetbrains.sbt
package language

import javax.swing.Icon

import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.plugins.scala.ScalaLanguage

/**
 * @author Pavel Fatin
 */
object SbtFileType extends LanguageFileType(ScalaLanguage.INSTANCE) {
  def getName: String = Sbt.Name

  def getDescription: String = Sbt.FileDescription

  def getDefaultExtension: String = Sbt.FileExtension

  def getIcon: Icon = Sbt.FileIcon
}
