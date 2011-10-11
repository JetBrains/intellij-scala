package org.jetbrains.plugins.scala.config
package ui

import com.intellij.util.PlatformIcons
import com.intellij.openapi.util.IconLoader
import java.awt.BorderLayout
import javax.swing._
import reflect.BeanProperty

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

  @BeanProperty
  var prefixLength = -1;

  override def getListCellRendererComponent(list: JList, value: Any, index: Int, 
                                            isSelected: Boolean, cellHasFocus: Boolean) = {
    val holder = Option(value.asInstanceOf[LibraryDescriptor])
    val html = htmlFor(holder)
    val enabled = comboBox.isEnabled
    lazy val plain = html.replaceAll("<.*?>", "").replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&nbsp;", " ")
    val result = super.getListCellRendererComponent(list, if (enabled) html else plain, index, isSelected, hasFocus)
    setIcon(if (enabled) ENABLED_ICON else DISABLED_ICON)
    if (prefixLength >= 0 && prefixLength == index) {
      val panel = new JPanel(new BorderLayout())
      panel.add(result, BorderLayout.CENTER)
      panel.add(new JSeparator(), BorderLayout.SOUTH)
      panel
    } else {
      result
    }
  }
}