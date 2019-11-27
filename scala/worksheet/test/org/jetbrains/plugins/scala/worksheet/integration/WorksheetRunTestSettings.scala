package org.jetbrains.plugins.scala.worksheet.integration

import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType

trait WorksheetRunTestSettings extends {

  def runType: WorksheetExternalRunType

  def runInCompileServerProcess: Boolean
}
