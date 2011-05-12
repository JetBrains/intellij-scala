package org.jetbrains.plugins.scala
package actions

import com.intellij.openapi.actionSystem.{DataConstants, AnActionEvent}
import lang.psi.api.ScalaFile

object ScalaActionUtil {
  def enableAndShowIfInScalaFile(e: AnActionEvent) {
    val presentation = e.getPresentation
    def enable() {
      presentation.setEnabled(true)
      presentation.setVisible(true)
    }
    def disable() {
      presentation.setEnabled(false)
      presentation.setVisible(false)
    }
    try {
      val dataContext = e.getDataContext
      val file = dataContext.getData(DataConstants.PSI_FILE)
      file match {
        case _: ScalaFile => enable()
        case _ => disable()
      }
    }
    catch {
      case e: Exception => disable()
    }
  }

}