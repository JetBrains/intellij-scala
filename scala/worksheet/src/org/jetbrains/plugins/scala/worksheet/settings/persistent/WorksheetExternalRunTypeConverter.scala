package org.jetbrains.plugins.scala.worksheet.settings.persistent

import com.intellij.util.xmlb.Converter
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType.ReplRunType

final class WorksheetExternalRunTypeConverter extends Converter[WorksheetExternalRunType] {

  override def fromString(value: String): WorksheetExternalRunType = {
    val found = WorksheetExternalRunType.findRunTypeByName(value)
    found.getOrElse(ReplRunType)
  }

  override def toString(value: WorksheetExternalRunType): String =
    value.getName
}