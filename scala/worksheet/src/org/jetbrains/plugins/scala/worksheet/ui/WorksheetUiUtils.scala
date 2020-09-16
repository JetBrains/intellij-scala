package org.jetbrains.plugins.scala.worksheet.ui

import java.awt.Dimension

import javax.swing.JComponent

object WorksheetUiUtils {

  def fixUnboundMaxSize(comp: JComponent, isSquare: Boolean = true): Unit = {
    val preferredSize = comp.getPreferredSize

    val size = if (isSquare) {
      val sqSize = Math.max(preferredSize.width, preferredSize.height)
      new Dimension(sqSize, sqSize)
    } else {
      new Dimension(preferredSize.width, preferredSize.height)
    }

    comp.setMaximumSize(size)
  }
}
