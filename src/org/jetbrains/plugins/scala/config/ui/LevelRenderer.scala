package org.jetbrains.plugins.scala.config.ui

import javax.swing.{DefaultListCellRenderer, JList}
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer.LibraryLevel

/**
 * Pavel.Fatin, 05.07.2010
 */

class LevelRenderer extends DefaultListCellRenderer {
  override def getListCellRendererComponent(list: JList, value: Any, index: Int, 
                                            isSelected: Boolean, cellHasFocus: Boolean) = {
    val text = Option(value.asInstanceOf[LibraryLevel]).map {
      _ match {
        case LibraryLevel.GLOBAL => "global"
        case LibraryLevel.PROJECT => "project-level"
        case LibraryLevel.MODULE => "module-level"
      }
    }
    super.getListCellRendererComponent(list, text.mkString, index, isSelected, hasFocus)
  }
}