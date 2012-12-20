package org.jetbrains.plugins.scala
package config.ui

import javax.swing.{DefaultListCellRenderer, JList}
import config.CompileOrder

/**
 * @author Pavel Fatin
 */
class CompileOrderRenderer extends DefaultListCellRenderer {
  override def getListCellRendererComponent(list: JList, value: Any, index: Int, 
                                            isSelected: Boolean, cellHasFocus: Boolean) = {
    val text = value match {
      case CompileOrder.Mixed => "Mixed"
      case CompileOrder.JavaThenScala => "Java then Scala"
      case CompileOrder.ScalaThenJava => "Scala then Java"
      case _ => "Unknown"
    }

    super.getListCellRendererComponent(list, text, index, isSelected, hasFocus)
  }
}
