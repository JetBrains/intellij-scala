package org.jetbrains.sbt

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.plugins.scala.ScalaFileType

/**
 * @author Pavel Fatin
 */
object SbtFileType extends LanguageFileType(ScalaFileType.SCALA_LANGUAGE) {
  def getName = "SBT"

  def getDescription = "SBT files"

  def getDefaultExtension = "sbt"

  def getIcon = AllIcons.Actions.Compile
}
