package intellijhocon
package lang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.icons.AllIcons

object HoconLanguageFileType extends LanguageFileType(HoconLanguage) {
  val DefaultExtension = "conf"

  def getIcon = AllIcons.FileTypes.Config

  def getDefaultExtension = DefaultExtension

  def getDescription = "Human-Optimized Config Object Notation"

  def getName = "HOCON"

}
