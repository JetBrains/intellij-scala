package org.jetbrains.plugins.scala.config
package ui

import java.awt.Component
import javax.swing.JList

import org.jetbrains.plugins.scala.lang.refactoring.util.DefaultListCellRendererAdapter

/**
 * Pavel.Fatin, 05.07.2010
 */

class LevelRenderer extends DefaultListCellRendererAdapter {
  def getListCellRendererComponentAdapter(list: JList[_], value: Any, index: Int,
                                            isSelected: Boolean, cellHasFocus: Boolean): Component = {
    val text = Option(value.asInstanceOf[LibraryLevel]).map {
      case LibraryLevel.Global => "global"
      case LibraryLevel.Project => "project-level"
      case LibraryLevel.Module => "module-level"
    }
    getSuperListCellRendererComponent(list, text.mkString, index, isSelected, hasFocus)
  }
}