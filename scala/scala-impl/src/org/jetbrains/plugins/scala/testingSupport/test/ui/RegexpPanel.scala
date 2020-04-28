package org.jetbrains.plugins.scala.testingSupport.test.ui

import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.table.JBTable
import com.intellij.ui.{AnActionButton, AnActionButtonRunnable, ToolbarDecorator}
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel
import org.jetbrains.plugins.scala.ScalaBundle

final class RegexpPanel extends JPanel {

  private var regexpTable: JBTable = _
  private var panel: JPanel  = _

  init()

  private def init(): Unit = {
    setLayout(new VerticalFlowLayout(VerticalFlowLayout.MIDDLE, 0, 5, true, false))
    regexpTable = createRegexpTable
    panel = createRegexpPanel(regexpTable)
    add(panel)
  }

  private def createRegexpTable: JBTable = {
    val table = new JBTable
    val model = table.getModel.asInstanceOf[DefaultTableModel]
    model.addColumn(ScalaBundle.message("test.run.config.for.class.pattern"))
    model.addColumn(ScalaBundle.message("test.run.config.test.pattern"))
    table
  }

  private def model: DefaultTableModel =
    regexpTable.getModel.asInstanceOf[DefaultTableModel]

  private def createRegexpPanel(regexpTable: JBTable) = {
    val addAction: AnActionButtonRunnable   = (_: AnActionButton) => {
      val editor = regexpTable.getCellEditor
      val rowAdd = regexpTable.getSelectedRow + 1

      if (editor != null)
        editor.stopCellEditing

      model.insertRow(rowAdd, Array[AnyRef]("", ""))
      if (rowAdd == 0)
        regexpTable.requestFocus()

      regexpTable.setRowSelectionInterval(rowAdd, rowAdd)
      regexpTable.setColumnSelectionInterval(0, 0)
    }

    val removeAction: AnActionButtonRunnable = (_: AnActionButton) => {
      val row = this.regexpTable.getSelectedRow
      if (row != -1) {
        val editor = this.regexpTable.getCellEditor

        if (editor != null)
          editor.stopCellEditing

        model.removeRow(row)

        if (row > 0) {
          this.regexpTable.setRowSelectionInterval(row - 1, row - 1)
          this.regexpTable.setColumnSelectionInterval(0, 0)
        }
      }
    }

    ToolbarDecorator.createDecorator(this.regexpTable)
      .setAddAction(addAction)
      .setRemoveAction(removeAction)
      .createPanel()
  }

  def setRegexps(classRegexps: Array[String], testRegexps: Array[String]): Unit = {
    val model = regexpTable.getModel.asInstanceOf[DefaultTableModel]
    val rows = classRegexps.zipAll(testRegexps, "", "").map { case (cr, tr) => Array[AnyRef](cr, tr) }
    rows.foreach(model.addRow)
  }

  def getRegexps: (Array[String], Array[String]) = {
    val model = regexpTable.getModel.asInstanceOf[DefaultTableModel]
    val column1 = Array.tabulate(model.getRowCount)(model.getValueAt(_, 0)).map(_.toString)
    val column2 = Array.tabulate(model.getRowCount)(model.getValueAt(_, 1)).map(_.toString)
    (column1, column2)
  }
}
