package org.jetbrains.sbt.project

import com.intellij.openapi.module.ModuleType
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleBridgeImpl

import java.awt.Component
import javax.swing.JTable
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
  }

  override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component = {
    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
    if (value == null ) {
      setText(defaultText)
    }
    this
  }
}
