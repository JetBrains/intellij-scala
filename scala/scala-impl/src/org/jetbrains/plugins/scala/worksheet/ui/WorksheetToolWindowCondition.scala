package org.jetbrains.plugins.scala.worksheet.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition

/**
  * User: Dmitry.Naydanov
  * Date: 29.05.18.
  */
class WorksheetToolWindowCondition extends Condition[Project] {
  override def value(t: Project): Boolean = false
}
