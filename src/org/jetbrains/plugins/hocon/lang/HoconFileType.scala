package org.jetbrains.plugins.hocon.lang

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType

object HoconFileType extends LanguageFileType(HoconLanguage) {
  val DefaultExtension = "conf"

  def getIcon = AllIcons.FileTypes.Config

  def getDefaultExtension = DefaultExtension

  def getDescription = "Human-Optimized Config Object Notation"

  def getName = "HOCON"

}
