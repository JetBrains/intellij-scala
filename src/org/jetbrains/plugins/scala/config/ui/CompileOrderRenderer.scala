package org.jetbrains.plugins.scala
package config.ui

import javax.swing.JList

import org.jetbrains.plugins.scala.config.CompileOrder
import org.jetbrains.plugins.scala.lang.refactoring.util.DefaultListCellRendererAdapter

/**
 * @author Pavel Fatin
 */
class CompileOrderRenderer extends DefaultListCellRendererAdapter {
  def getListCellRendererComponentAdapter(list: JList[_], value: Any, index: Int,
                                            isSelected: Boolean, cellHasFocus: Boolean) = {
    val text = value match {
      case CompileOrder.Mixed => "Mixed"
      case CompileOrder.JavaThenScala => "Java then Scala"
      case CompileOrder.ScalaThenJava => "Scala then Java"
      case _ => "Unknown"
    }

    getSuperListCellRendererComponent(list, text, index, isSelected, hasFocus)
  }
}
