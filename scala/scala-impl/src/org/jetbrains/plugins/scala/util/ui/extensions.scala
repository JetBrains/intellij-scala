package org.jetbrains.plugins.scala.util.ui

import java.awt.Component

import javax.swing.JComponent

object extensions {

  implicit class JComponentExt(private val target: JComponent) extends AnyVal {

    def components: Iterator[Component] =
      Iterator.tabulate(target.getComponentCount)(target.getComponent)
  }
}
