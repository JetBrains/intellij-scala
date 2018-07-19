package org.jetbrains.plugins.scala.worksheet.cell

import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompilerUtil
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompilerUtil.RunOuter

/**
  * User: Dmitry.Naydanov
  * Date: 05.06.18.
  */
class RunCellAction(cellDescriptor: CellDescriptor) extends RunCellActionBase(cellDescriptor) {
  override def convertToRunRequest(cellText: String): WorksheetCompilerUtil.WorksheetCompileRunRequest = RunOuter(cellText)
}
