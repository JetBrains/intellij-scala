package org.jetbrains.sbt.language

import org.jetbrains.plugins.scala.LanguageFileTypeBase
import org.jetbrains.sbt.icons.Icons

import javax.swing.Icon

object SbtFileType extends LanguageFileTypeBase(SbtLanguage.INSTANCE) {
  override def getIcon: Icon = Icons.SBT_FILE
}
