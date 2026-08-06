package org.jetbrains.plugins.scala.structuralSearch

import org.jetbrains.plugins.scala.{LanguageFileTypeBase, Scala3Language}
import org.jetbrains.plugins.scala.icons.Icons

import javax.swing.Icon

object Scala3FileType extends LanguageFileTypeBase(Scala3Language.INSTANCE) {
  def getExtensionWithDot: String = "." + getDefaultExtension

  override def getIcon: Icon = Icons.SCALA_FILE
}
