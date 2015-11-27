package org.jetbrains.plugins.scala.worksheet.actions

import javax.swing.JPanel

/**
  * User: Dmitry.Naydanov
  * Date: 23.11.15.
  */
trait TopComponentDisplayable {
  def init(panel: JPanel): Unit
}
