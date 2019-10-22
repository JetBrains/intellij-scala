package org.jetbrains.plugins.scala.worksheet.ui

import java.awt.Component

import javax.swing.JComponent

package object extensions {

  implicit class JComponentExt(private val target: JComponent) extends AnyVal {

    def components: Iterator[Component] =
      Iterator.tabulate(target.getComponentCount)(target.getComponent)
  }
}
