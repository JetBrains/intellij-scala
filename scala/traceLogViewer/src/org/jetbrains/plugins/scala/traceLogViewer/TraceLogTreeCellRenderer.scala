package org.jetbrains.plugins.scala.traceLogViewer

import java.awt.{Color, Component, Graphics}
import javax.swing.JTable
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableCellRenderer

class TraceLogTreeCellRenderer extends DefaultTableCellRenderer {
  private var currentNode: TraceLogModel.Node = _

  val colors = Seq(
    Color.GREEN.darker().darker(),
    Color.RED.darker().darker(),
    Color.YELLOW.darker().darker(),
    Color.CYAN.darker().darker(),
  )

  val indent = 8

  override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean,
                                             hasFocus: Boolean, row: Int, column: Int): Component = {
    val node = value.asInstanceOf[TraceLogModel.Node]
    currentNode = node
    this.setText(node.msg)
    this.setBorder(new EmptyBorder(0, indent * node.depth + 3, 0, 0))
    this
  }

  override def paint(g: Graphics): Unit = {
    def color(depth: Int) = colors(depth % colors.size)

    val saveColor = g.getColor
    val height = getHeight
    val width = getWidth

    //if (!currentNode.isLeaf) {
    //  g.setColor(color(currentNode.depth))
    //  g.fillRect(0, 0, width, height)
    //}

    for (i <- 0 until currentNode.depth) {
      g.setColor(color(i))
      g.fillRect(i * indent, 0, indent * currentNode.depth, height)
    }
    g.setColor(saveColor)

    super.paint(g)
  }
}
