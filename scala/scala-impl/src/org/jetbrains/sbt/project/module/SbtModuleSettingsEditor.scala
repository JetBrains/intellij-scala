package org.jetbrains.sbt
package project.module

import java.awt.event.ActionEvent
import java.util.Collections

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.{ModuleConfigurationState, ModuleElementsEditor}
import com.intellij.ui.CollectionListModel
import com.intellij.util.text.DateFormatUtil
import javax.swing.JPanel
import javax.swing.event.ListSelectionEvent
import javax.swing.table.AbstractTableModel
import org.jetbrains.plugins.scala.util.JListCompatibility
import org.jetbrains.sbt.resolvers.indexes.ResolverIndex
import org.jetbrains.sbt.resolvers.{SbtIndexesManager, SbtResolver}
import org.jetbrains.sbt.settings.SbtSettings

import scala.jdk.CollectionConverters._

/**
 * @author Nikolay Obedin
 * @since 12/1/14.
 */
class SbtModuleSettingsEditor (state: ModuleConfigurationState) extends ModuleElementsEditor(state) {

  import SbtModule._

  private val myForm = new SbtModuleSettingsForm
  private val modelWrapper = new JListCompatibility.CollectionListModelWrapper(new CollectionListModel[String](Collections.emptyList[String]))
  private val resolvers = Resolvers(getModel.getModule).toSeq

  override def getDisplayName: String = SbtBundle.message("sbt.settings.sbtModuleSettings")

  override def saveData(): Unit = {}

  override def createComponentImpl(): JPanel = {
    myForm.sbtImportsList.setEmptyText(SbtBundle.message("sbt.settings.noImplicitImportsFound"))
    JListCompatibility.setModel(myForm.sbtImportsList, modelWrapper.getModelRaw)

    myForm.updateButton.addActionListener((e: ActionEvent) => {
      val resolversToUpdate: Seq[SbtResolver] = myForm.resolversTable.getSelectedRows.map(resolvers(_))
      SbtIndexesManager.getInstance(state.getProject).foreach(_.updateWithProgress(resolversToUpdate))
    })

    myForm.mainPanel
  }

  override def reset(): Unit = {
    val module = getModel.getModule
    val moduleSettings = SbtSettings.getInstance(state.getProject).getLinkedProjectSettings(module)
    myForm.sbtVersionTextField.setText(moduleSettings.map(_.sbtVersion).getOrElse(SbtBundle.message("sbt.settings.sbtVersionNotDetected")))

    modelWrapper.getModel.replaceAll(Imports(module).asJava)

    myForm.resolversTable.setModel(new ResolversModel(resolvers, state.getProject))
    if (myForm.resolversTable.getRowCount > 0)
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
      myForm.updateButton.setEnabled(value != SbtBundle.message("sbt.settings.resolvers.mavenUnavailable"))
    } catch {
      case _: IndexOutOfBoundsException => myForm.updateButton.setEnabled(false)  // no resolvers in project?
    }
  }
}

private class ResolversModel(val resolvers: Seq[SbtResolver], val project:Project) extends AbstractTableModel {

  private val columns = Seq(
    SbtBundle.message("sbt.settings.resolvers.name"),
    SbtBundle.message("sbt.settings.resolvers.url"),
    SbtBundle.message("sbt.settings.resolvers.updated")
  )

  override def getColumnCount: Int = columns.size

  override def getRowCount: Int = resolvers.size

  override def getColumnName(columnIndex: Int): String = columns(columnIndex)

  override def getValueAt(rowIndex: Int, columnIndex: Int): String = {
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
            SbtBundle.message("sbt.settings.resolvers.neverUpdated")
          else if (ts == ResolverIndex.MAVEN_UNAVALIABLE)
            SbtBundle.message("sbt.settings.resolvers.mavenUnavailable")
          else
            DateFormatUtil.formatDate(ts)
        }

    }
    valueOpt.getOrElse("???")
  }
}
