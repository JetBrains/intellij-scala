package org.jetbrains.plugins.scala.worksheet.actions.topmenu

import javax.swing.JPanel

trait TopComponentDisplayable {

  def init(panel: JPanel): Unit
}
