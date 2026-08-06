package org.jetbrains.plugins.scala.testingSupport
package test.ui

import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.table.JBTable
import com.intellij.ui.{AnActionButton, AnActionButtonRunnable, ToolbarDecorator}
import org.jetbrains.plugins.scala.testingSupport.test.ui.RegexpPanel._

import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

final class RegexpPanel extends JPanel {

  private var myRegexpTable: JBTable = _
  private var myPanel: JPanel  = _

  init()

  private def init(): Unit = {
    setLayout(new VerticalFlowLayout(VerticalFlowLayout.MIDDLE, 0, 5, true, false))
    myRegexpTable = createRegexpTable
    myPanel = createRegexpPanel(myRegexpTable)
    add(myPanel)
  }

  private def createRegexpTable: JBTable = {
    val table = new JBTable
    val model = table.getModel.asInstanceOf[DefaultTableModel]
    model.addColumn(TestingSupportBundle.message("test.run.config.for.class.pattern"))
    model.addColumn(TestingSupportBundle.message("test.run.config.test.pattern"))
    table
  }

  private def createRegexpPanel(regexpTable: JBTable): JPanel = {
    val addAction: AnActionButtonRunnable   = (_: AnActionButton) => {
      val editor = regexpTable.getCellEditor
      val rowAdd = regexpTable.getSelectedRow + 1

      if (editor != null)
        editor.stopCellEditing

      regexpTable.model.insertRow(rowAdd, Array[AnyRef]("", ""))
      if (rowAdd == 0)
        regexpTable.requestFocus()

      regexpTable.setRowSelectionInterval(rowAdd, rowAdd)
      regexpTable.setColumnSelectionInterval(0, 0)
    }

    val removeAction: AnActionButtonRunnable = (_: AnActionButton) => {
      val row = regexpTable.getSelectedRow
      if (row != -1) {
        val editor = regexpTable.getCellEditor

        if (editor != null)
          editor.stopCellEditing

        regexpTable.model.removeRow(row)

        if (row > 0) {
          regexpTable.setRowSelectionInterval(row - 1, row - 1)
          regexpTable.setColumnSelectionInterval(0, 0)
        }
      }
    }

    ToolbarDecorator.createDecorator(regexpTable)
      .setAddAction(addAction)
      .setRemoveAction(removeAction)
      .createPanel()
  }

  def setRegexps(classRegexps: Array[String], testRegexps: Array[String]): Unit = {
    val model = myRegexpTable.model
    model.removeRows()
    val rows = classRegexps.zipAll(testRegexps, "", "").map { case (cr, tr) => Array[AnyRef](cr, tr) }
    rows.foreach(model.addRow)
  }

  def getRegexps: (Array[String], Array[String]) = {
    val model = myRegexpTable.model
    val column1 = Array.tabulate(model.getRowCount)(model.getValueAt(_, 0)).map(_.toString)
    val column2 = Array.tabulate(model.getRowCount)(model.getValueAt(_, 1)).map(_.toString)
    (column1, column2)
  }
}

private object RegexpPanel {

  implicit class JBTableExt(private val table: JBTable) extends AnyVal {
    def model: DefaultTableModel = table.getModel.asInstanceOf[DefaultTableModel]
  }

  implicit class DefaultTableModelExt(private val model: DefaultTableModel) extends AnyVal {
    def removeRows(): Unit =
      for (rowIdx <- (0 until model.getRowCount).reverse)
        model.removeRow(rowIdx)
  }
}
