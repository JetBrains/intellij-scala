package org.jetbrains.plugins.scala.traceLogViewer

import java.awt.{Color, Component, Graphics}
import javax.swing.JTable
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableCellRenderer

class TraceLogFunctionCellRenderer extends DefaultTableCellRenderer {
  private var currentNode: TraceLogModel.Node = _

  val colors = Seq(
    Color.GREEN.darker().darker(),
    Color.RED.darker().darker(),
    Color.YELLOW.darker().darker(),
    Color.CYAN.darker().darker(),
  )

  val indentSize = 8

  override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean,
                                             hasFocus: Boolean, row: Int, column: Int): Component = {
    val node = value.asInstanceOf[TraceLogModel.Node]
    currentNode = node
    this.setText(
      if (node.msg != "") ""
      else node.stackTrace.headOption.fold("")(_.method)
    )
    this.setBorder(new EmptyBorder(0, indentSize * (node.depth + 1) + 3, 0, 0))
    this
  }

  override def paint(g: Graphics): Unit = {
    super.paint(g)

    def color(depth: Int) = colors(depth % colors.size)

    val saveColor = g.getColor
    val height = getHeight
    val width = getWidth

    //if (!currentNode.isLeaf) {
    //  g.setColor(color(currentNode.depth))
    //  g.fillRect(0, 0, width, height)
    //}
    def drawIndent(i: Int, color: Color): Unit = {
      g.setColor(color)
      g.fillRect(i * indentSize, 0, indentSize, height)
    }
    val depth = currentNode.depth
    for (i <- 0 until depth)
      drawIndent(i, color(i))
    drawIndent(depth, if (currentNode.isLeaf) Color.LIGHT_GRAY else color(depth))

    g.setColor(saveColor)
  }
}
