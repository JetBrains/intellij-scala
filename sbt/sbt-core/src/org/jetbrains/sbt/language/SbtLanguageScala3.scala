package org.jetbrains.sbt.language

import com.intellij.lang.{DependentLanguage, Language}
import com.intellij.openapi.fileTypes.LanguageFileType

final class SbtLanguageScala3 private
  extends Language(SbtLanguage.INSTANCE, "sbt Scala 3")
    with DependentLanguage {

  override def getAssociatedFileType: LanguageFileType = SbtFileType
}

object SbtLanguageScala3 {
  val INSTANCE = new SbtLanguageScala3
}
