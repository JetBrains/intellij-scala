package org.jetbrains.plugins.scala.config.ui

import javax.swing.{DefaultListCellRenderer, JList}
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer.LibraryLevel
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.config.ScalaLibrary

/**
 * Pavel.Fatin, 05.07.2010
 */

class LibraryRenderer extends DefaultListCellRenderer {
  override def getListCellRendererComponent(list: JList, value: Any, index: Int, 
                                            isSelected: Boolean, cellHasFocus: Boolean) = {
    val html = Option(value.asInstanceOf[ScalaLibrary]).map { library =>
      val levelName = library.level match {
        case LibraryLevel.GLOBAL => "global"
        case LibraryLevel.PROJECT => "project-level"
        case LibraryLevel.MODULE => "module-level"
      }
      """<html><body>%s <span style="color: #808080;">(%s, %s)</span>&nbsp;</body></html>"""
              .format(library.name, library.version, levelName)
    }
    val result = super.getListCellRendererComponent(list, html.getOrElse("<No SDK>"), index, isSelected, hasFocus)
    setIcon(if(html.isDefined) Icons.SCALA_SDK else Icons.NO_SCALA_SDK)
    result
  }
}