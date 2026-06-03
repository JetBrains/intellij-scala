package org.jetbrains.plugins.scala.worksheet.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import org.jetbrains.plugins.scala.extensions.OptionExt
import org.jetbrains.plugins.scala.worksheet.WorksheetFile

object WorksheetActionUtil {
  @inline def getWorksheetFileFrom(event: AnActionEvent): Option[WorksheetFile] =
    ScalaActionUtil.getFileFrom(event).filterByType[WorksheetFile]
}
