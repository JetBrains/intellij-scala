package org.jetbrains.plugins.scala
package config.ui

import javax.swing.JList

import org.jetbrains.plugins.scala.config.DebuggingInfoLevel
import org.jetbrains.plugins.scala.lang.refactoring.util.DefaultListCellRendererAdapter

/**
 * Nikolay.Tropin
 * 2014-08-05
 */
class DebuggingInfoRenderer extends DefaultListCellRendererAdapter {

  def getListCellRendererComponentAdapter(list: JList[_], value: Any, index: Int,
                                          isSelected: Boolean, cellHasFocus: Boolean) = {
    val text = value match {
      case level: DebuggingInfoLevel => level.getDescription
      case _ => ""
    }
    getSuperListCellRendererComponent(list, text, index, isSelected, hasFocus)
  }
}