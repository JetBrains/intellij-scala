package org.jetbrains.sbt
package project.module


import javax.swing.table.AbstractTableModel

import com.intellij.openapi.roots.ui.configuration.{ModuleConfigurationState, ModuleElementsEditor}
import org.jetbrains.sbt.resolvers.{SbtResolver, SbtResolverIndexesManager}


/**
 * @author Nikolay Obedin
 * @since 7/23/14.
 */
class SbtModuleResolversEditor(state: ModuleConfigurationState) extends ModuleElementsEditor(state) {

  private val myForm = new SbtModuleResolversForm

  def getDisplayName = "Resolvers"

  def getHelpTopic = null

  def createComponentImpl() = myForm.mainPanel

  override def reset() {
    SbtResolverIndexesManager.getInstance.test
    val resolvers = SbtModule.getResolversFrom(getModel.getModule).toSeq
    myForm.resolversTable.setModel(new ResolversModel(resolvers))
    myForm.resolversTable.getColumnModel.getColumn(0).setPreferredWidth(50);
    myForm.resolversTable.getColumnModel.getColumn(1).setPreferredWidth(400);
    myForm.resolversTable.getColumnModel.getColumn(2).setPreferredWidth(20);
  }

  def saveData() {}
}

private class ResolversModel(val resolvers: Seq[SbtResolver]) extends AbstractTableModel {

  private val columns = Seq("Name", "URL", "Updated")

  def getColumnCount = columns.size

  def getRowCount = resolvers.size

  override def getColumnName(columnIndex: Int) = columns(columnIndex)

  def getValueAt(rowIndex: Int, columnIndex: Int) = columnIndex match {
    case 0 => resolvers(rowIndex).name
    case 1 => resolvers(rowIndex).root
    case 2 => "Never" // FIXME: fix this when resolver manager is done
  }
}
