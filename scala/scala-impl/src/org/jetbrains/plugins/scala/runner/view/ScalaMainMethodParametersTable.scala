package org.jetbrains.plugins.scala.runner
package view

import com.intellij.execution.util.StringWithNewLinesCellEditor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.TableView
import com.intellij.util.ui.{ColumnInfo, JBUI, ListTableModel}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.applyTo
import org.jetbrains.plugins.scala.runner.Scala3MainMethodSyntheticClass.MainMethodParameters.CustomParameter
import org.jetbrains.plugins.scala.runner.view.ScalaMainMethodParametersTable._

import javax.swing.table.TableCellEditor
import javax.swing.{JComponent, ListSelectionModel}
import scala.jdk.CollectionConverters.{IterableHasAsScala, SeqHasAsJava}

private class ScalaMainMethodParametersTable(
  parameters: Seq[CustomParameter],
  parametersValues: Seq[String]
) {

  assert(
    parametersValues.size <= parameters.size,
    "the dialog should not be shown when application configuration has enough parameters"
  )

  def filledParametersValues: Seq[String] =
    myTableView.getItems.asScala.toSeq.map(_.value)

  def finishCellEditing(): Unit = {
    myTableView.stopEditing()
  }

  private val myTableView: TableView[MyTableViewModelItem] = {
    val items = parameters.zipWithIndex.map { case (param, idx) =>
      val value = parametersValues.lift(idx).getOrElse("")
      new MyTableViewModelItem(param, value)
    }

    val model = new ListTableModel[MyTableViewModelItem](
      ParamNameColumnInfo,
      ParamTypeColumnInfo,
      ParamValueColumnInfo
    )
    applyTo(model)(
      _.setItems(items.asJava),
      _.setSortable(false),
    )

    val tableView = new TableView[MyTableViewModelItem](model)
    applyTo(tableView)(
      _.setShowGrid(true),
      _.setIntercellSpacing(JBUI.emptySize),
      _.getTableHeader,
      _.setSelectionMode(ListSelectionModel.SINGLE_SELECTION),
    )
  }

  private object ParamNameColumnInfo extends ColumnInfo[MyTableViewModelItem, String](ScalaBundle.message("main.method.parameters.table.column.title.name")) {
    override def valueOf(item: MyTableViewModelItem): String = {
      item.parameter.name
    }
  }
  private object ParamTypeColumnInfo extends ColumnInfo[MyTableViewModelItem, String](ScalaBundle.message("main.method.parameters.table.column.title.type")) {
    override def valueOf(item: MyTableViewModelItem): String = {
      val param = item.parameter
      param.typ + (if (param.isVararg) "*" else "")
    }
  }
  private object ParamValueColumnInfo extends ColumnInfo[MyTableViewModelItem, String](ScalaBundle.message("main.method.parameters.table.column.title.value")) {
    override def valueOf(item: MyTableViewModelItem): String = item.value

    override def isCellEditable(item: MyTableViewModelItem): Boolean = true

    override def getEditor(item: MyTableViewModelItem): TableCellEditor = new StringWithNewLinesCellEditor

    override def setValue(item: MyTableViewModelItem, value: String): Unit = {
      item.value = value
    }
  }

  private val myRootPanel: JComponent = new JBScrollPane(myTableView)

  def getComponent: JComponent = myRootPanel
}

private object ScalaMainMethodParametersTable {

  private class MyTableViewModelItem(
    val parameter: CustomParameter,
    var value: String
  )
}
