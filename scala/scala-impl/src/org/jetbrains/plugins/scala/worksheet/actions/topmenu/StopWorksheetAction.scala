package org.jetbrains.plugins.scala.worksheet.actions.topmenu

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import javax.swing.Icon
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.compiler.CompilationProcess

class StopWorksheetAction(exec: CompilationProcess) extends AnAction with TopComponentAction {

  override def genericText: String = ScalaBundle.message("worksheet.stop.button")

  override def actionIcon: Icon = AllIcons.Actions.Suspend

  override def actionPerformed(e: AnActionEvent): Unit = exec.stop()
}