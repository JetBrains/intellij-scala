package org.jetbrains.plugins.scala
package components

import javax.swing.Icon

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import org.jetbrains.plugins.scala.compiler.CompilationProcess
import worksheet.actions.TopComponentAction

/**
 * User: Dmitry Naydanov
 * Date: 2/17/14
 */
class StopWorksheetAction(exec: CompilationProcess) extends AnAction with TopComponentAction {
  override def actionPerformed(e: AnActionEvent) {
    exec.stop()
  }

  override def actionIcon: Icon = AllIcons.Actions.Suspend

  override def bundleKey = "worksheet.stop.button"
}