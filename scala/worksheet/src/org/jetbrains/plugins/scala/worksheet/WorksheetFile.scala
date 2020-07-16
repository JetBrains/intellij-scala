package org.jetbrains.plugins.scala.worksheet

import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.worksheet.settings.{WorksheetExternalRunType, WorksheetFileSettings}

final class WorksheetFile(viewProvider: FileViewProvider)
  extends ScalaFileImpl(viewProvider, WorksheetFileType) {

  override def isWorksheetFile = true

  override def isMultipleDeclarationsAllowed: Boolean = {
    val runType = WorksheetFileSettings(this).getRunTypeOpt
    !runType.contains(WorksheetExternalRunType.PlainRunType)
  }
}