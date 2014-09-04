package org.jetbrains.plugins.scala.config.ui

import java.awt.Color
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.{JTable, UIManager}

import com.intellij.util.ui.{ColumnInfo, ListTableModel}
import org.jetbrains.plugins.scala.config.CompilerPlugin

/**
 * Pavel.Fatin, 31.07.2010
 */


class CompilerPluginsTableModel extends ListTableModel[CompilerPlugin](PathColumn, NameColumn)

object PathColumn extends ColumnInfo[CompilerPlugin, (String, Boolean)]("Path") {
  def valueOf(item: CompilerPlugin) = (item.path, item.file.exists)

  override def getRenderer(item: CompilerPlugin) = CompilerPluginRenderer

  override def getPreferredStringValue = "c:\\program files\\scala\\plugins\\scala-plugin.jar"
}

object NameColumn extends ColumnInfo[CompilerPlugin, String]("Name") {
  def valueOf(item: CompilerPlugin) = item.name

  override def getPreferredStringValue = "continuations"
}

object CompilerPluginRenderer extends DefaultTableCellRenderer {
  private val normalForeground = UIManager.getColor("Table.foreground")
  private val selectionForeground = UIManager.getColor("Table.selectionForeground")

  override def getTableCellRendererComponent(table: JTable, value: AnyRef,
                                             isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int) = {
    val (path, exists) = value.asInstanceOf[(String, Boolean)]

    val result = super.getTableCellRendererComponent(table, path, isSelected, hasFocus, row, column)

    val foreground = if (isSelected) selectionForeground else normalForeground
    setForeground(if (exists) foreground else Color.RED)

    result
  }
}
