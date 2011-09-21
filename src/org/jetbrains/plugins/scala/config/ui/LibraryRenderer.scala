package org.jetbrains.plugins.scala.config
package ui

import javax.swing.{DefaultListCellRenderer, JList}
import com.intellij.util.PlatformIcons

/**
 * Pavel.Fatin, 05.07.2010
 */

class LibraryRenderer extends DefaultListCellRenderer {
  val Empty = """<html><body><span style="color: #ff0000;">&lt;none&gt;</span>&nbsp;</body></html>"""
  val NotFound = """<html><body><span style="color: #ff0000;">%s [not found]</span>&nbsp;</body></html>"""
  val Unknown = """<html><body>%s&nbsp;</html>"""
  val Normal = """<html><body>%s <span style="color: #808080;">(version %s)</span>&nbsp;</body></html>"""

  def nameOf(level: LibraryLevel) = level match {
    case LibraryLevel.Global => "global"
    case LibraryLevel.Project => "project-level"
    case LibraryLevel.Module => "module-level"
  }                                       

  def htmlFor(descriptor: Option[LibraryDescriptor]) = descriptor match {
    case Some(LibraryDescriptor(id, data)) => data match {
      case Some(data) => { 
        if(data.version.isEmpty) 
          Unknown.format(id.name)
        else 
          Normal.format(id.name, data.version.get)
      }
      case None => NotFound.format(id.name)
    }
    case None => Empty
  }

  override def getListCellRendererComponent(list: JList, value: Any, index: Int, 
                                            isSelected: Boolean, cellHasFocus: Boolean) = {
    val holder = Option(value.asInstanceOf[LibraryDescriptor])
    val html = htmlFor(holder)
    val result = super.getListCellRendererComponent(list, html, index, isSelected, hasFocus)
    setIcon(PlatformIcons.LIBRARY_ICON)
    result
  }
}