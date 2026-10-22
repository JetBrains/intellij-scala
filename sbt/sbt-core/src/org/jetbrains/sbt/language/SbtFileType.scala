package org.jetbrains.sbt.language

import org.jetbrains.plugins.scala.LanguageFileTypeBase
import org.jetbrains.sbt.icons.Icons

import javax.swing.Icon

object SbtFileType extends LanguageFileTypeBase(SbtLanguage.INSTANCE) {
  override def getIcon: Icon = Icons.SBT_FILE

  // Registers the Scala 3 dialect together with the file type (`Language.findLanguageByID` finds only instantiated
  // languages). In Remote Development the backend sends the language substitution of sbt files by id, and the frontend
  // instantiates the file type, but nothing else would instantiate the dialect: the files would be parsed as plain text.
  // See also ScalaLanguage
  locally(SbtLanguageScala3.INSTANCE)
}
