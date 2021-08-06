package org.jetbrains.plugins.scala.traceLogViewer

import com.intellij.ui.dualView.TreeTableView
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.util.ui.ColumnInfo
import org.jetbrains.plugins.scala.extensions.ObjectExt

import java.awt.event.{MouseAdapter, MouseEvent}

trait ClickableColumn[Item] { this: ColumnInfo[Item, ?] =>
  def onClick(view: TreeTableView, e: MouseEvent, item: Item, row: Int): Unit
}

object ClickableColumn {
  def install(table: TreeTableView): Unit = {
    assert(table.getTableModel.is[ListTreeTableModelOnColumns])

    table.addMouseListener(new MouseAdapter {
      override def mousePressed(e: MouseEvent): Unit = {
        val point = e.getPoint
        val column = table.columnAtPoint(point)
        val model = table.getTableModel.asInstanceOf[ListTreeTableModelOnColumns]
        model.getColumns.lift(column) match {
          case Some(clickableColumn: ClickableColumn[Any] @unchecked) =>
            val row = table.rowAtPoint(point)
            if (row < table.getRowCount) {
              clickableColumn.onClick(table, e, table.getValueAt(row, column), row)
            }
          case _ =>
        }
      }
    })
  }
}