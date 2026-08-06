package org.jetbrains.plugins.scala.caches.stats

import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.table.TableView
import com.intellij.ui.{SpeedSearchComparator, TableViewSpeedSearch}
import com.intellij.util.ui.{ColumnInfo, ListTableModel}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions.invokeLater

import java.awt.event.{FocusEvent, FocusListener}
import java.util.Comparator
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}
import javax.swing.JTable
import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

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

  private def column[T](@Nls name: String, value: Data => T, comparator: Comparator[T]): ColumnInfo[String, T] =

    new ColumnInfo[String, T](name) {

      override def valueOf(id: String): T = value(getData(id))

      override def getComparator: Comparator[String] = Comparator.comparing[String, T](valueOf _, comparator)
    }

  def stringColumn(@Nls name: String, value: Data => String): ColumnInfo[String, String] =
    column(name, value, Comparator.naturalOrder())

  def numColumn[T: Numeric](@Nls name: String, value: Data => T): ColumnInfo[String, T] =
    column(name, value, implicitly[Numeric[T]])
}

class DataByIdTableModel[Data](dataById: DataById[Data],
                               columnInfos: ColumnInfo[String, _]*)
                              (preferredWidths: Seq[Int])

  extends ListTableModel[String](columnInfos: _*) {

  private val comparator = new SpeedSearchComparator(false)
  private var currentPattern: String = ""

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

  def registerSpeedSearch(table: TableView[String]): Unit = {
    @nowarn("cat=deprecation")
    val speedSearch = new TableViewSpeedSearch(table) {
      override def getItemText(id: String): String = rowText(id)

      override def onSearchFieldUpdated(pattern: String): Unit = {
        currentPattern = pattern
      }
    }

    table.addFocusListener(new FocusListener {
      override def focusGained(e: FocusEvent): Unit = restorePopup(speedSearch)

      override def focusLost(e: FocusEvent): Unit = restorePopup(speedSearch)
    })
  }

  private def restorePopup(speedSearch: TableViewSpeedSearch[String]): Unit = {
    if (StringUtil.isNotEmpty(currentPattern)) invokeLater {
      speedSearch.showPopup(currentPattern)
    }
  }

  def clear(): Unit = {
    dataById.clear()
    setItems(new java.util.ArrayList())
  }

  def refresh(data: java.util.List[Data]): Unit = {
    val pattern = currentPattern
    refresh(data, text => pattern.isEmpty || comparator.matchingDegree(pattern, text) > 0)
  }

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
