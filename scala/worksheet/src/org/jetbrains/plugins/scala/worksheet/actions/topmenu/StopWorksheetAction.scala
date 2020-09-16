package org.jetbrains.plugins.scala.worksheet.actions.topmenu

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import javax.swing.Icon
import org.jetbrains.plugins.scala.worksheet.WorksheetBundle
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.StopWorksheetAction.StoppableProcess

class StopWorksheetAction(private var process: Option[StoppableProcess]) extends AnAction with TopComponentAction {

  override def genericText: String = WorksheetBundle.message("worksheet.stop.button")

  override def actionIcon: Icon = AllIcons.Actions.Suspend

  override def actionPerformed(e: AnActionEvent): Unit = process.foreach(_.stop())

  def setStoppableProcess(process: Option[StoppableProcess]): Unit = this.process = process
}

object StopWorksheetAction {

  trait StoppableProcess {
    def stop(): Unit
  }
}