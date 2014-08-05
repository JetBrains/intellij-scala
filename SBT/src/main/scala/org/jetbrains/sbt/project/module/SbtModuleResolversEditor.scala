package org.jetbrains.sbt
package project.module


import java.awt.event.{ActionEvent, ActionListener}
import javax.swing.table.AbstractTableModel

import com.intellij.openapi.roots.ui.configuration.{ModuleConfigurationState, ModuleElementsEditor}
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.sbt.resolvers.{SbtResolver, SbtResolverIndex, SbtResolverIndexesManager}


/**
 * @author Nikolay Obedin
 * @since 7/23/14.
 */
class SbtModuleResolversEditor(state: ModuleConfigurationState) extends ModuleElementsEditor(state) {

  private val myForm = new SbtModuleResolversForm
  private val resolvers = SbtModule.getResolversFrom(getModel.getModule).toSeq

  def getDisplayName = SbtBundle("sbt.resolversForm.title")

  def getHelpTopic = null

  def createComponentImpl() = {
    myForm.updateButton.addActionListener(new ActionListener {
      override def actionPerformed(e: ActionEvent): Unit = {
        val resolversToUpdate: Seq[SbtResolver] =
          myForm.resolversTable.getSelectedRows map (resolvers(_))
        SbtResolverIndexesManager().update(resolversToUpdate)
      }
    })
    myForm.mainPanel
  }

  override def reset() {
    myForm.resolversTable.setModel(new ResolversModel(resolvers))
    myForm.resolversTable.getColumnModel.getColumn(0).setPreferredWidth(50);
    myForm.resolversTable.getColumnModel.getColumn(1).setPreferredWidth(400);
    myForm.resolversTable.getColumnModel.getColumn(2).setPreferredWidth(20);
  }

  def saveData() {}
}

private class ResolversModel(val resolvers: Seq[SbtResolver]) extends AbstractTableModel {

  private val columns = Seq(SbtBundle("sbt.resolversForm.name"),
                            SbtBundle("sbt.resolversForm.url"),
                            SbtBundle("sbt.resolversForm.updated"))

  def getColumnCount = columns.size

  def getRowCount = resolvers.size

  override def getColumnName(columnIndex: Int) = columns(columnIndex)

  def getValueAt(rowIndex: Int, columnIndex: Int) = columnIndex match {
    case 0 => resolvers(rowIndex).name
    case 1 => resolvers(rowIndex).root
    case 2 =>
      val ts: Long = resolvers(rowIndex).associatedIndex.map(_.timestamp).getOrElse(SbtResolverIndex.NO_TIMESTAMP)
      if (ts == SbtResolverIndex.NO_TIMESTAMP) SbtBundle("sbt.resolversForm.neverUpdated") else DateFormatUtil.formatDate(ts)
  }
}
