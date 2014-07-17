package org.jetbrains.plugins.scala
package lang.formatting.automatic.action

import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext, AnActionEvent, AnAction}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import org.jetbrains.plugins.scala.actions.ScalaActionUtil

/**
 * Created by Roman.Shein on 16.07.2014.
 */
class ToggleAutoFormatterUseAction extends AnAction {
  override def actionPerformed(e: AnActionEvent) {
    println("toggle autoformatter use action is performed")
    ScalaBlock.toggleAutoFormatter()
  }

  override def update(e: AnActionEvent) {
    ScalaActionUtil enableAndShowIfInScalaFile e
  }
}