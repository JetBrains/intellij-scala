package org.jetbrains.plugins.scala.caches.stats

import java.util.Comparator
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}

import com.intellij.ui.TableViewSpeedSearch
import com.intellij.ui.table.TableView
import com.intellij.util.ui.{ColumnInfo, ListTableModel}
import javax.swing.JTable

import scala.collection.JavaConverters.enumerationAsScalaIteratorConverter

class DataById[Data](id: Data => String) {
  private val dataBuffer: ConcurrentMap[String, Data] = new ConcurrentHashMap()

  def getId(data: Data): String = id(data)

  def getData(id: String): Data = dataBuffer.get(id)

  def forEachId(f: String => Unit): Unit = dataBuffer.keySet().forEach(f(_))

  def clear(): Unit = {
    dataBuffer.clear()
  }

  def refresh(newData: java.util.List[Data]): Unit = {
    dataBuffer.clear()
    newData.forEach(d => dataBuffer.put(getId(d), d))
  }

  private def column[T](name: String, value: Data => T, comparator: Comparator[T]): ColumnInfo[String, T] =

    new ColumnInfo[String, T](name) {

      override def valueOf(id: String): T = value(getData(id))

      override def getComparator: Comparator[String] = Comparator.comparing[String, T](valueOf _, comparator)
    }

  def stringColumn(name: String, value: Data => String): ColumnInfo[String, String] =
    column(name, value, Comparator.naturalOrder())

  def numColumn[T: Numeric](name: String, value: Data => T): ColumnInfo[String, T] =
    column(name, value, implicitly[Numeric[T]])
}

class DataByIdTableModel[Data](dataById: DataById[Data],
                               columnInfos: ColumnInfo[String, _]*)
                              (preferredWidths: Seq[Int])

  extends ListTableModel[String](columnInfos: _*) {

  private var currentFilter: String => Boolean = Function.const(true)

  private def rowText(id: String): String = {
    columnInfos.map(_.valueOf(id)).mkString(" ")
  }

  def fixColumnWidth(table: JTable): Unit = {
    val widths = preferredWidths.iterator
    val tableColumns = table.getColumnModel.getColumns.asScala
    widths.zip(tableColumns).foreach {
      case (w, column) =>
        column.setPreferredWidth(100 * w)
    }
  }

  def createTable(): TableView[String] = {
    val table = new TableView[String](this)
    fixColumnWidth(table)
    table
  }

  def registerSpeedSearch(table: TableView[String]): Unit =
    new TableViewSpeedSearch(table) {
      override def getItemText(id: String): String = rowText(id)

      override def onSearchFieldUpdated(pattern: String): Unit = {
        currentFilter = (text: String) => {
          pattern.isEmpty || !isPopupActive ||
            getComparator.matchingDegree(pattern, text) > 0
        }
      }
  }

  def clear(): Unit = {
    dataById.clear()
    setItems(new java.util.ArrayList())
  }

  def refresh(data: java.util.List[Data]): Unit = refresh(data, currentFilter)

  private def refresh(data: java.util.List[Data], filter: String => Boolean): Unit = {
    def matches(id: String): Boolean = filter(rowText(id))

    def removeRows(): Unit = {
      var idx = getRowCount - 1
      while (idx >= 0) {
        val id = getRowValue(idx)
        if (dataById.getData(id) == null || !matches(id)) {
          removeRow(idx)
        }

        idx -= 1
      }
    }

    dataById.refresh(data)

    removeRows()

    dataById.forEachId { id =>
      if (!matches(id)) ()
      else {
        indexOf(id) match {
          case -1  => addRow(id)
          case idx => fireTableRowsUpdated(idx, idx)
        }
      }
    }
  }
}
