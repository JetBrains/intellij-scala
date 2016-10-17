package org.jetbrains.plugins.scala.codeInspection.bundled

import javax.swing.event.TableModelListener
import javax.swing.table.TableModel
import java.util

import com.intellij.openapi.project.Project

import scala.collection.mutable

/**
  * User: Dmitry.Naydanov
  * Date: 06.10.16.
  */
class BundledInspectionsUiTableModel(pathToInspections: util.Map[String, util.ArrayList[String]], 
                                     disabledIds: util.Set[String], project: Project) extends TableModel {
  private val rows = {
    val result = mutable.ArrayBuffer[(java.lang.Boolean, String, String, String)]()

    val idsToNames = BundledInspectionStoreComponent.getInstance(project).getLoadedInspections.map(i => (i.getId, i.getName)).toMap
    val it = pathToInspections.entrySet().iterator()
    
    while (it.hasNext) {
      val entry = it.next()
      val jarName = entry.getKey
      val il = entry.getValue.iterator()
      
      while (il.hasNext) {
        val insName = il.next()
        
        result.+=((new java.lang.Boolean(!disabledIds.contains(insName)), idsToNames.getOrElse(insName, "No name"), insName, jarName))
      }
    }
    
    result.toArray
  }
  
  
  override def getRowCount: Int = rows.length

  override def getColumnClass(columnIndex: Int): Class[_] = if (columnIndex == 0) classOf[java.lang.Boolean] else classOf[String]

  override def isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == 0

  override def getColumnCount: Int = 4

  override def getColumnName(columnIndex: Int): String = columnIndex match {
    case 0 => "Is enabled"
    case 1 => "Name"
    case 2 => "Class"
    case 3 => "Corresponding jar"
    case _ => ""
  }

  override def removeTableModelListener(l: TableModelListener): Unit = {}

  override def addTableModelListener(l: TableModelListener): Unit = {}

  override def getValueAt(rowIndex: Int, columnIndex: Int): AnyRef = {
    val v = rows.apply(rowIndex)
    
    columnIndex match {
      case 0 => v._1
      case 1 => v._2
      case 2 => v._3
      case 3 => v._4
      case _ => ""
    }
  }

  override def setValueAt(aValue: scala.Any, rowIndex: Int, columnIndex: Int): Unit = {
    if (columnIndex != 0) return
    
    aValue match {
      case b: Boolean => 
        val (_, a1, a2, a3) = rows.apply(rowIndex)
        rows(rowIndex) = (b, a1, a2, a3)
      case _ => 
    }
  }
  
  def getDisabledIdsWithPreservedOrder = {
    val set = new util.HashSet[String]()
    rows.foreach {
      case (cb, _, id, _) if !cb.booleanValue() => set.add(id)
      case _ => 
    }
    set
  }
}
