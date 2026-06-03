package org.jetbrains.plugins.scala.testingSupport.test.ui

import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager}
import com.intellij.util.ui.JBUI

import java.awt.Component
import javax.swing.{JComponent, JPanel}

private class GridUiBuilder(panel: JPanel, val columns: Int) {
  private var _rowIdx = 0
  def rowIdx: Int = _rowIdx
  panel.setLayout(new GridLayoutManager(99, columns, JBUI.emptyInsets, -1, -1))

  def append[T <: JComponent](component: T): T =
    append(component, constraint(_rowIdx)(this))

  def append[T <: JComponent](component: T, constraints: Any): T = {
    panel.add(component, constraints)
    _rowIdx += 1
    component
  }

  def appendRow(row: JComponent*): Unit = {
    assert(row.size <= columns)
    row.zipWithIndex.foreach { case (component: Component, idx: Int) =>
      val colSpan = if (idx == row.size - 1) columns - idx else 1
      val c = constraint(_rowIdx, idx, colSpan)
      panel.add(component, c)
    }
    _rowIdx += 1
  }

  def constraint(row: Int)(implicit builder: GridUiBuilder): GridConstraints =
    constraint(row, 0, builder.columns)

  def constraint(row: Int, col: Int, colSpan: Int): GridConstraints =
    new GridConstraints(
      row, col, 1, colSpan,
      GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_WANT_GROW | GridConstraints.SIZEPOLICY_CAN_GROW,
      GridConstraints.SIZEPOLICY_FIXED,
      null, null, null, 0, false
    )
}