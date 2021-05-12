package org.jetbrains.plugins.scala.traceLogViewer.viewer

import java.awt.Component
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

abstract class TraceLogBaseCellRenderer extends DefaultTableCellRenderer {
  protected var currentNode: TraceLogModel.Node = _

  final override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean,
                                             hasFocus: Boolean, row: Int, column: Int): Component = {
    currentNode = value.asInstanceOf[TraceLogModel.Node]
    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
    setup()
    this
  }

  def setup(): Unit = ()
}
