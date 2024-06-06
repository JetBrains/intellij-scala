package org.jetbrains.sbt.project

import com.intellij.openapi.module.ModuleType
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleBridgeImpl

import java.awt.Component
import javax.swing.{BorderFactory, JTable}
import javax.swing.table.DefaultTableCellRenderer

class ModuleComboBoxColumnCellRenderer(defaultText: String) extends DefaultTableCellRenderer {
  override def setValue(value: Any): Unit = {
    value match {
      case module: ModuleBridgeImpl =>
        setText(module.getName)
        setIcon(ModuleType.get(module).getIcon)
      case _ =>
        setIcon(null)
        super.setValue(value)
    }
    // note: it is needed, because when the cell with ModulesComboBox is active (the user clicks on it), ModulesComboBox is responsible for displaying the values in a cell
    // and it has a margin. Without adding a border the value in a cell would change its position depending on whether it is active or not.
    setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0))
  }


  override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component = {
    val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
    if (value == null ) {
      setText(defaultText)
    }
    component
  }
}
