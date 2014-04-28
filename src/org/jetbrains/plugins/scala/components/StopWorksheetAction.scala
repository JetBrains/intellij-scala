package org.jetbrains.plugins.scala
package components

import com.intellij.openapi.actionSystem.{AnActionEvent, AnAction}
import org.jetbrains.plugins.scala.worksheet.actions.TopComponentAction
import javax.swing.Icon
import com.intellij.icons.AllIcons

/**
 * User: Dmitry Naydanov
 * Date: 2/17/14
 */
class StopWorksheetAction(exec: WorksheetProcess) extends AnAction with TopComponentAction {
  override def actionPerformed(e: AnActionEvent) {
    exec.stop()
  }

  override def update(e: AnActionEvent): Unit = super.update(e)

  override def actionIcon: Icon = AllIcons.Actions.Suspend

  override def bundleKey = "worksheet.stop.button"
}

trait WorksheetProcess {
  def run()
  
  def stop()
  
  def addTerminationCallback(callback: => Unit)
}