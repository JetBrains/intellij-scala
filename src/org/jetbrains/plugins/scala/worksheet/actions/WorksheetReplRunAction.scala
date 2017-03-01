package org.jetbrains.plugins.scala.worksheet.actions

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CustomShortcutSet}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler

/**
  * User: Dmitry.Naydanov
  * Date: 27.02.17.
  */
class WorksheetReplRunAction extends AnAction with WorksheetAction {
  setShortcutSet(CustomShortcutSet.fromString("ctrl ENTER"))
  setInjectedContext(false)

  override def update(e: AnActionEvent): Unit = {
    updateInner(e)
  }

  override def actionPerformed(e: AnActionEvent): Unit = {
    RunWorksheetAction.runCompiler(e.getProject, auto = false)
  }

  override def acceptFile(file: ScalaFile): Boolean = WorksheetCompiler.isWorksheetReplMode(file)
}

object WorksheetReplRunAction {
  val ACTION_INSTANCE = new WorksheetReplRunAction
}