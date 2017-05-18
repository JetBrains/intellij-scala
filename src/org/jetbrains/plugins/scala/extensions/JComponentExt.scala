package org.jetbrains.plugins.scala.extensions

import java.awt.event.{ActionEvent, ActionListener}
import javax.swing.JComponent

import scala.language.reflectiveCalls

/**
  * Nikolay.Tropin
  * 18-May-17
  */
object JComponentExt {
  private type WithAddActionListener = JComponent {
    def addActionListener(al: ActionListener)
  }

  //todo: replace with SAM after migration to scala 2.12
  implicit class ActionListenersOwner(val jc: WithAddActionListener) extends AnyVal {
    def addActionListenerEx(body: => Unit): Unit = jc.addActionListener(new ActionListener {
      override def actionPerformed(e: ActionEvent): Unit = body
    })
  }
}
