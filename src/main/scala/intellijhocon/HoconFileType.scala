package intellijhocon

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.icons.AllIcons

object HoconFileType extends LanguageFileType(HoconLanguage) {
  val DefaultExtension = "conf"

  def getIcon = AllIcons.FileTypes.Config

  def getDefaultExtension = DefaultExtension

  def getDescription = "Human-Optimized Config Object Notation"

  def getName = "HOCON"
}
