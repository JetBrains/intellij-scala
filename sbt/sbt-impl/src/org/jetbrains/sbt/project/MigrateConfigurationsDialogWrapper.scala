package org.jetbrains.sbt.project

import com.intellij.openapi.ui.{ComboBox, DialogWrapper}
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.openapi.project.Project
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager}

import java.awt.{Component, Dimension, Font}
import javax.swing._
import javax.swing.event.TableModelEvent
import javax.swing.table.{DefaultTableCellRenderer, DefaultTableModel}
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.ui.SimpleListCellRenderer
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.SbtMigrateConfigurationsAction.ModuleHeuristicResult

import java.awt.event.MouseEvent
import scala.collection.mutable

class MigrateConfigurationsDialogWrapper(project: Project, configurationToModule: Map[ApplicationConfiguration, ModuleHeuristicResult]) extends DialogWrapper(true) {

  private val myTable = new JBTable() {
    override def getToolTipText(event: MouseEvent): String = {
      val row = this.rowAtPoint(event.getPoint)
      if (row >= 0 && row < configurationToModule.size) {
        val configurationOpt = findConfigInRow(row)
        val mostLikelyModuleNames = configurationOpt.flatMap(configurationToModule.get).map(_.guesses)
        mostLikelyModuleNames match {
          case Some(guesses) if guesses.nonEmpty => guesses.mkString("Suggested modules: ", ", ", "")
          case _ => super.getToolTipText(event)
        }
      } else {
        super.getToolTipText(event)
      }
    }
  }

  private val myTableModel = new DefaultTableModel(Array[AnyRef]("Run Configuration name", "Previous module name", "New module"), configurationToModule.size - 1)
  private var result: mutable.Map[ApplicationConfiguration, Option[Module]] = mutable.Map.empty

  locally {
    setTitle(SbtBundle.message("sbt.migrate.configurations.dialog.wrapper.title"))
    myTable.setModel(myTableModel)
    setModal(true)
    init()
  }

  override def createCenterPanel(): JComponent = {
    setUpColumnForSelectingModules()
    setUpTableHeaderRenderer()

    configurationToModule.foreach { case(k, v) =>
      val configName = k.getName
      val configurationModuleName = k.getConfigurationModule.getModuleName
      val row: Array[AnyRef] = v.module match {
        case Some(module) => Array(configName, configurationModuleName, module)
        case _ => Array(configName, configurationModuleName)
      }
      myTableModel.addRow(row)
    }

    myTableModel.addTableModelListener((e: TableModelEvent) => {
      val row = e.getFirstRow
      val configOpt = findConfigInRow(row)
      configOpt match {
        case Some(config) =>
          val newModule = myTableModel.getValueAt(row, 2)
          if (result.isEmpty) {
            result = mutable.Map() ++ configurationToModule.view.mapValues(_.module).toMap
          }
          result.update(config, Some(newModule.asInstanceOf[Module]))
        case None =>
      }
    })

    getOKAction.setEnabled(true)

    val scrollPane = new JBScrollPane
    scrollPane.setViewportView(myTable)
    val panel = new JPanel
    panel.setLayout(new GridLayoutManager(1, 1))
    panel.add(scrollPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(600, 300), null, 0, false))
    panel
  }

  private def setUpTableHeaderRenderer(): Unit =
    myTable.getTableHeader.setDefaultRenderer(new DefaultTableCellRenderer() {
      override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component = {
        val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        component match {
          case label: JLabel =>
            val font = label.getFont
            label.setFont(font.deriveFont(font.getStyle | Font.BOLD))
            label
          case c => c
        }
      }
    })

  private def setUpColumnForSelectingModules(): Unit = {
    val defaultText = SbtBundle.message("sbt.migrate.configurations.dialog.wrapper.default")

    val comboBox = createModuleComboBox(defaultText)
    val editor = new DefaultCellEditor(comboBox)

    val columnModel = myTable.getColumnModel.getColumn(2)
    columnModel.setCellEditor(editor)
    columnModel.setCellRenderer(new ModuleComboBoxColumnCellRenderer(defaultText))
  }

  private def createModuleComboBox(defaultText: String): ComboBox[Module] = {
    val comboBox = new ComboBox[Module]()
    comboBox.setRenderer(new SimpleListCellRenderer[Module]() {
      override def customize(list: JList[_ <: Module], value: Module, index: Int, selected: Boolean, hasFocus: Boolean): Unit = {
        val text =
          if (value == null) defaultText
          else value.getName

        setText(text)
      }
    })
    ModuleManager.getInstance(project).getModules.foreach(comboBox.addItem)
    comboBox
  }

  override def doCancelAction(): Unit = {
    super.doCancelAction()
    onCancel()
  }

  override def doOKAction(): Unit = {
    super.doOKAction()
    disposeDialog()
  }

  private def onCancel(): Unit =
    closeDialogGracefully()

  def open(): Map[ApplicationConfiguration, Option[Module]] = {
    pack()
    show()
    result.toMap
  }

  private def findConfigInRow(i: Integer): Option[ApplicationConfiguration] = {
    val runConfiguration = myTableModel.getValueAt(i, 0).asInstanceOf[String]
    configurationToModule.find(_._1.getName == runConfiguration).map(_._1)
  }

  private def closeDialogGracefully(): Unit = {
    result = mutable.Map.empty[ApplicationConfiguration, Option[Module]]
    disposeDialog()
  }

  private def disposeDialog(): Unit =
    dispose()
}

