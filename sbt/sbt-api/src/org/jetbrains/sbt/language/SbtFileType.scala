package org.jetbrains.sbt.language

import com.intellij.openapi.util.IconLoader
import org.jetbrains.plugins.scala.LanguageFileTypeBase

import javax.swing.Icon

object SbtFileType extends LanguageFileTypeBase(SbtLanguage.INSTANCE) {

  override def getIcon: Icon =
    IconLoader.getIcon("/org/jetbrains/sbt/images/sbtFile.svg", this.getClass)
}
