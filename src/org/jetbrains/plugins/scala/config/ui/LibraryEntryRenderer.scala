package org.jetbrains.plugins.scala.config.ui

import javax.swing.{DefaultListCellRenderer, JList}
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer.LibraryLevel
import org.jetbrains.plugins.scala.config.LibraryEntry
import com.intellij.util.Icons

/**
 * Pavel.Fatin, 05.07.2010
 */

class LibraryEntryRenderer extends DefaultListCellRenderer {
  private val Template = """<html><body>%s <span style="color: #808080;">(%s, %s)</span>&nbsp;</body></html>"""
  private val None = """<html><body><span style="color: #ff0000;">&lt;none&gt;</span></body></html>"""
  
  override def getListCellRendererComponent(list: JList, value: Any, index: Int, 
                                            isSelected: Boolean, cellHasFocus: Boolean) = {
    val html = Option(value.asInstanceOf[LibraryEntry]).map { library =>
      val levelName = library.level match {
        case LibraryLevel.GLOBAL => "global"
        case LibraryLevel.PROJECT => "project-level"
        case LibraryLevel.MODULE => "module-level"
      }
      Template.format(library.name, library.version, levelName)
    }
    val result = super.getListCellRendererComponent(list, html.getOrElse(None), index, isSelected, hasFocus)
    setIcon(Icons.LIBRARY_ICON)
    result
  }
}