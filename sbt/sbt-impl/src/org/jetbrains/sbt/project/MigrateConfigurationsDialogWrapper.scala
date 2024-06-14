package org.jetbrains.sbt.project

import com.intellij.application.options.ModulesComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager}

import java.awt.{Dimension, Font}
import javax.swing._
import javax.swing.table.{DefaultTableCellRenderer, DefaultTableModel}
import com.intellij.execution.configurations.{ModuleBasedConfiguration, RunConfigurationModule}
import com.intellij.openapi.module.Module
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.SbtMigrateConfigurationsAction.ModuleHeuristicResult

import java.awt.event.MouseEvent
import scala.jdk.CollectionConverters.IterableHasAsJava

class MigrateConfigurationsDialogWrapper(modules: Array[Module], configurationToModule: Map[ModuleBasedConfiguration[_, _], ModuleHeuristicResult]) extends DialogWrapper(true) {

  private val myTable = new JBTable() {
    override def getToolTipText(event: MouseEvent): String = {
      // TODO The tooltip doesn't work when the user just clicks on it.
      //  In order for a tooltip to appear, the user has to move the mouse a little after clicking on it.
      val row = rowAtPoint(event.getPoint)
      val configurationOpt = findConfigInRow(row)
      val guesses = configurationOpt.flatMap(configurationToModule.get).map(_.guesses).getOrElse(Nil)
      if (guesses.nonEmpty) {
        guesses.mkString("Suggested modules: ", ", ", "")
      } else {
        super.getToolTipText(event)
      }
    }
  }

  private val myTableModel = new DefaultTableModel(Array[AnyRef]("Configuration", "Module name in previous scheme", "New module"), 0) {
    override def isCellEditable(row: Int, column: Int): Boolean = column != 0 && column != 1
  }

  private var isClosed: Boolean = false

  locally {
    setTitle(SbtBundle.message("sbt.migrate.configurations.dialog.wrapper.title"))
    myTable.setModel(myTableModel)
    setModal(true)
    init()
  }

  override def createCenterPanel(): JComponent = {
    setUpTableHeaderRenderer()
    setUpConfigurationColumn()
    setUpPreviousModuleNameColumn()
    setUpSelectingModulesColumn()

    configurationToModule.foreach { case (config, ModuleHeuristicResult(module, _)) =>
      val moduleName = config.getConfigurationModule.asInstanceOf[RunConfigurationModule].getModuleName
      val row: Array[AnyRef] = module match {
        case Some(module) => Array(config, moduleName, module)
        case _ => Array(config, moduleName)
      }
      myTableModel.addRow(row)
    }

    getOKAction.setEnabled(true)

    val scrollPane = new JBScrollPane
    scrollPane.setViewportView(myTable)
    val panel = new JPanel
    panel.setLayout(new GridLayoutManager(1, 1))
    panel.add(scrollPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(600, 300), null, 0, false))
    panel
  }

  override def doCancelAction(): Unit = {
    super.doCancelAction()
    isClosed = true
    dispose()
  }

  override def doOKAction(): Unit = {
    super.doOKAction()
    dispose()
  }

  private def getValueAt[T](row: Integer, column: Integer): Option[T] =
    Option(myTableModel.getValueAt(row, column)).map(_.asInstanceOf[T])

  private def setUpTableHeaderRenderer(): Unit = {
    val font = myTable.getTableHeader.getFont
    myTable.getTableHeader.setFont(font.deriveFont(font.getStyle | Font.BOLD))
  }

  private def setUpSelectingModulesColumn(): Unit = {
    val defaultText = SbtBundle.message("sbt.migrate.configurations.dialog.wrapper.default")

    val comboBox = new ModulesComboBox()
    comboBox.setModules(modules.toSeq.asJavaCollection)
    comboBox.allowEmptySelection(defaultText)
    val editor = new DefaultCellEditor(comboBox)

    val columnModel = myTable.getColumnModel.getColumn(2)
    columnModel.setCellEditor(editor)
    columnModel.setCellRenderer(new ModuleComboBoxColumnCellRenderer(defaultText))
  }

  private def setUpConfigurationColumn(): Unit = {
    val columnModel = myTable.getColumnModel.getColumn(0)
    columnModel.setCellRenderer(new DefaultTableCellRenderer() {
      override def setValue(value: Any): Unit = {
        value match {
          case x: ModuleBasedConfiguration[_, _] =>
            setIcon(x.getType.getIcon)
            setText(x.getName)
          case _ =>
        }
      }
    })
  }

  private def setUpPreviousModuleNameColumn(): Unit = {
    // note: it is needed because column name "Module name in previous scheme" is too long
    val columnModel = myTable.getColumnModel.getColumn(1)
    columnModel.setPreferredWidth(150)
  }

  def open(): Map[ModuleBasedConfiguration[_, _], Module] = {
    pack()
    show()
    if (!isClosed) getCurrentTableState
    else Map.empty
  }

  private def getCurrentTableState: Map[ModuleBasedConfiguration[_, _], Module] = {
    val rowsCount = myTableModel.getRowCount
    (0 until rowsCount)
      .map(createConfigModuleTuple)
      .collect { case (Some(config), Some(module)) => config -> module }
      .toMap
  }

  private def createConfigModuleTuple(row: Int): (Option[ModuleBasedConfiguration[_, _]], Option[Module]) = {
    val config = getValueAt[ModuleBasedConfiguration[_, _]](row, 0)
    val module = getValueAt[Module](row, 2)
    (config, module)
  }

  private def findConfigInRow(row: Integer): Option[ModuleBasedConfiguration[_, _]] = {
    val isRowWithinRange = row >= 0 && row < configurationToModule.size
    if (isRowWithinRange) getValueAt[ModuleBasedConfiguration[_, _]](row, 0)
    else None
  }
}

