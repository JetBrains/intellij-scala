package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}

/**
 * A no-op action to collect statistics.
 */
class XRayModeAction extends AnAction {
  override def update(e: AnActionEvent): Unit = {
    e.getPresentation.setVisible(false) // Don't show in Find Action
  }

  override def actionPerformed(e: AnActionEvent): Unit = ()
}

object XRayModeAction {
  final val Id = "Scala.XRayMode"
}
