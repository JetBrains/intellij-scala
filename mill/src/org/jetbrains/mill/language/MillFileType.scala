package org.jetbrains.mill.language

import org.jetbrains.plugins.scala.{LanguageFileTypeBase, ScalaLanguage}
import org.jetbrains.mill.icons.Icons

import javax.swing.Icon

object MillFileType extends LanguageFileTypeBase(ScalaLanguage.INSTANCE) {
  override def getIcon: Icon = Icons.MILL_FILE
}
