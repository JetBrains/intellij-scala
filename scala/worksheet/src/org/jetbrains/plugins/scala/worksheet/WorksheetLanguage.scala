package org.jetbrains.plugins.scala.worksheet

import com.intellij.lang.Language
import org.jetbrains.plugins.scala.ScalaLanguage

final class WorksheetLanguage private()
  extends Language(ScalaLanguage.INSTANCE, "Scala Worksheet")
    with WorksheetLanguageLike

object WorksheetLanguage {
  final val INSTANCE = new WorksheetLanguage
}