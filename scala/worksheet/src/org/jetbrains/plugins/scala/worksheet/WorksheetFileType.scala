package org.jetbrains.plugins.scala.worksheet

import javax.swing.Icon
import org.jetbrains.plugins.scala.finder.FileTypeWithIsolatedDeclarations
import org.jetbrains.plugins.scala.{LanguageFileTypeBase, ScalaFileType}

object WorksheetFileType
  extends LanguageFileTypeBase(WorksheetLanguage.INSTANCE)
    with FileTypeWithIsolatedDeclarations {

  override def getDefaultExtension = "sc"

  // TODO worksheet logo
  override val getIcon: Icon = ScalaFileType.INSTANCE.getIcon
}