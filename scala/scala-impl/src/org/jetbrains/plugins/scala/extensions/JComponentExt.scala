package org.jetbrains.plugins.scala.extensions

import java.awt.event.ActionListener
import javax.swing.JComponent
import scala.language.reflectiveCalls

object JComponentExt {
  private type WithAddActionListener = JComponent {
    def addActionListener(al: ActionListener): Unit
  }

  implicit class ActionListenersOwner(private val jc: WithAddActionListener) extends AnyVal {
    def addActionListenerEx(body: => Unit): Unit = jc.addActionListener(_ => body)
  }
}
