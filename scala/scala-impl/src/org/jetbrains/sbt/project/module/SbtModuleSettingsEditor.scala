package org.jetbrains.sbt
package project.module

import java.awt.event.{ActionEvent, ActionListener}
import java.util
import javax.swing.JPanel
import javax.swing.event.{ListSelectionEvent, ListSelectionListener}
import javax.swing.table.AbstractTableModel

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.{ModuleConfigurationState, ModuleElementsEditor}
import com.intellij.ui.CollectionListModel
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.plugins.scala.util.JListCompatibility
import org.jetbrains.plugins.scala.util.JListCompatibility.CollectionListModelWrapper
import org.jetbrains.sbt.resolvers.indexes.ResolverIndex
import org.jetbrains.sbt.resolvers.{SbtIndexesManager, SbtResolver}
import org.jetbrains.sbt.settings.SbtSettings

import scala.collection.JavaConverters._

/**
 * @author Nikolay Obedin
 * @since 12/1/14.
 */
class SbtModuleSettingsEditor (state: ModuleConfigurationState) extends ModuleElementsEditor(state) {
  private val myForm = new SbtModuleSettingsForm
  private val modelWrapper = new CollectionListModelWrapper(new CollectionListModel[String](new util.ArrayList[String]))
  private val resolvers = SbtModule.getResolversFrom(getModel.getModule).toSeq

  override def getDisplayName: String = SbtBundle("sbt.settings.sbtModuleSettings")

  override def saveData(): Unit = {}

  override def createComponentImpl(): JPanel = {
    myForm.sbtImportsList.setEmptyText(SbtBundle("sbt.settings.noImplicitImportsFound"))
    JListCompatibility.setModel(myForm.sbtImportsList, modelWrapper.getModelRaw)

    myForm.updateButton.addActionListener((e: ActionEvent) => {
      val resolversToUpdate: Seq[SbtResolver] = myForm.resolversTable.getSelectedRows map (resolvers(_))
      SbtIndexesManager.getInstance(state.getProject).foreach(_.updateWithProgress(resolversToUpdate))
    })

    myForm.mainPanel
  }

  override def reset(): Unit = {
    val moduleSettings = SbtSettings.getInstance(state.getProject).getLinkedProjectSettings(getModel.getModule)
    myForm.sbtVersionTextField.setText(moduleSettings.map(_.sbtVersion).getOrElse(SbtBundle("sbt.settings.sbtVersionNotDetected")))

    modelWrapper.getModel.replaceAll(SbtModule.getImportsFrom(getModel.getModule).asJava)

    myForm.resolversTable.setModel(new ResolversModel(resolvers, state.getProject))
    myForm.resolversTable.setRowSelectionInterval(0, 0)
    myForm.resolversTable.getColumnModel.getColumn(0).setPreferredWidth(50)
    myForm.resolversTable.getColumnModel.getColumn(1).setPreferredWidth(400)
    myForm.resolversTable.getColumnModel.getColumn(2).setPreferredWidth(30)
    myForm.resolversTable.getSelectionModel.addListSelectionListener((_: ListSelectionEvent) => setupUpdateButton())
    setupUpdateButton()
  }

  def setupUpdateButton(): Unit = {
    // use first element in model to do availability checking if no ros has yet been selected
    val selectedRow = Option(myForm.resolversTable.getSelectedRow).filter(_ >= 0).getOrElse(0)
    try {
      val value = myForm.resolversTable.getModel.getValueAt(selectedRow, 2)
      myForm.updateButton.setEnabled(value != SbtBundle("sbt.settings.resolvers.mavenUnavailable"))
    } catch {
      case _: IndexOutOfBoundsException => myForm.updateButton.setEnabled(false)  // no resolvers in project?
    }
  }
}

private class ResolversModel(val resolvers: Seq[SbtResolver], val project:Project) extends AbstractTableModel {

  private val columns = Seq(
    SbtBundle("sbt.settings.resolvers.name"),
    SbtBundle("sbt.settings.resolvers.url"),
    SbtBundle("sbt.settings.resolvers.updated")
  )

  def getColumnCount: Int = columns.size

  def getRowCount: Int = resolvers.size

  override def getColumnName(columnIndex: Int): String = columns(columnIndex)

  def getValueAt(rowIndex: Int, columnIndex: Int): String = {
    val valueOpt = columnIndex match {
      case 0 => resolvers.lift(rowIndex).map(_.name)
      case 1 => resolvers.lift(rowIndex).map(_.root)
      case 2 =>
        for {
          resolver <- resolvers.lift(rowIndex)
          index <- resolver.getIndex(project)
        } yield {
          val ts = index.getUpdateTimeStamp
          if (ts == ResolverIndex.NO_TIMESTAMP)
            SbtBundle("sbt.settings.resolvers.neverUpdated")
          else if (ts == ResolverIndex.MAVEN_UNAVALIABLE)
            SbtBundle("sbt.settings.resolvers.mavenUnavailable")
          else
            DateFormatUtil.formatDate(ts)
        }

    }
    valueOpt.getOrElse("???")
  }
}
