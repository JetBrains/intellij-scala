package org.jetbrains.plugins.scala.worksheet

import com.intellij.lang.Language
import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.worksheet.settings.{WorksheetExternalRunType, WorksheetFileSettings}

final class WorksheetFile(viewProvider: FileViewProvider, language: Language with WorksheetLanguageLike)
  extends ScalaFileImpl(viewProvider, WorksheetFileType, language) {

  override def toString: String = "WorksheetFile: " + getName

  override def isWorksheetFile = true

  override def isMultipleDeclarationsAllowed: Boolean = {
    val runType = WorksheetFileSettings(this).getRunTypeOpt
    !runType.contains(WorksheetExternalRunType.PlainRunType)
  }
}