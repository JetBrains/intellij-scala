package org.jetbrains.plugins.scala.worksheet

import com.intellij.lang.DependentLanguage
import com.intellij.lang.Language
import org.jetbrains.plugins.scala.ScalaLanguage

final class WorksheetLanguage private()
  extends Language(ScalaLanguage.INSTANCE, "Scala Worksheet")
    with DependentLanguage
    with WorksheetLanguageLike

object WorksheetLanguage {
  final val INSTANCE = new WorksheetLanguage
}