package org.jetbrains.plugins.scala.worksheet

import com.intellij.lang.{DependentLanguage, Language}
import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.plugins.scala.Scala3Language

final class WorksheetLanguage3 private()
  extends Language(Scala3Language.INSTANCE, "Scala 3 Worksheet")
    with DependentLanguage
    with WorksheetLanguageLike {

  /**
   *  no need in overriding for [[WorksheetLanguage]] cause it inferes WorksheetFileType automatically
   * (see super implementation getAssociatedFileType)
   */
  override def getAssociatedFileType: LanguageFileType = WorksheetFileType
}

object WorksheetLanguage3 {
  final val INSTANCE = new WorksheetLanguage3
}
