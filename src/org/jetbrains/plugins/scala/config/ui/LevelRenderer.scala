package org.jetbrains.plugins.scala.config
package ui

import javax.swing.{DefaultListCellRenderer, JList}

/**
 * Pavel.Fatin, 05.07.2010
 */

class LevelRenderer extends DefaultListCellRenderer {
  override def getListCellRendererComponent(list: JList, value: Any, index: Int, 
                                            isSelected: Boolean, cellHasFocus: Boolean) = {
    val text = Option(value.asInstanceOf[LibraryLevel]).map {
      _ match {
        case LibraryLevel.Global => "global"
        case LibraryLevel.Project => "project-level"
        case LibraryLevel.Module => "module-level"
      }
    }
    super.getListCellRendererComponent(list, text.mkString, index, isSelected, hasFocus)
  }
}