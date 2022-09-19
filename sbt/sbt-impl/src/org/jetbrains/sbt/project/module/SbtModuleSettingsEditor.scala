package org.jetbrains.sbt
package project.module

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.{ModuleConfigurationState, ModuleElementsEditor}
import com.intellij.ui.CollectionListModel
import org.jetbrains.sbt.resolvers.SbtResolver
import org.jetbrains.sbt.settings.SbtSettings

import java.util.Collections
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel
import scala.jdk.CollectionConverters._

class SbtModuleSettingsEditor (state: ModuleConfigurationState) extends ModuleElementsEditor(state) {

  import SbtModule._

  private val myForm = new SbtModuleSettingsForm
  private val model = new CollectionListModel[String](Collections.emptyList)
  private val resolvers = Resolvers(getModel.getModule).toSeq

  override def getDisplayName: String = SbtBundle.message("sbt.settings.sbtModuleSettings")

  override def saveData(): Unit = {}

  override def createComponentImpl(): JPanel = {
    myForm.sbtImportsList.setEmptyText(SbtBundle.message("sbt.settings.noImplicitImportsFound"))
    myForm.sbtImportsList.setModel(model)

    myForm.mainPanel
  }

  override def reset(): Unit = {
    val module = getModel.getModule
    val moduleSettings = SbtSettings.getInstance(state.getProject).getLinkedProjectSettings(module)
    myForm.sbtVersionTextField.setText(moduleSettings.map(_.sbtVersion).getOrElse(SbtBundle.message("sbt.settings.sbtVersionNotDetected")))

    model.replaceAll(Imports(module).asJava)

    myForm.resolversTable.setModel(new ResolversModel(resolvers, state.getProject))
    if (myForm.resolversTable.getRowCount > 0)
      myForm.resolversTable.setRowSelectionInterval(0, 0)
    myForm.resolversTable.getColumnModel.getColumn(0).setPreferredWidth(50)
    myForm.resolversTable.getColumnModel.getColumn(1).setPreferredWidth(400)
  }
}

private class ResolversModel(val resolvers: Seq[SbtResolver], val project:Project) extends AbstractTableModel {

  private val columns = Seq(
    SbtBundle.message("sbt.settings.resolvers.name"),
    SbtBundle.message("sbt.settings.resolvers.url")
  )

  override def getColumnCount: Int = columns.size

  override def getRowCount: Int = resolvers.size

  override def getColumnName(columnIndex: Int): String = columns(columnIndex)

  override def getValueAt(rowIndex: Int, columnIndex: Int): String = {
    val valueOpt = columnIndex match {
      case 0 => resolvers.lift(rowIndex).map(_.name)
      case 1 => resolvers.lift(rowIndex).map(_.root)
      case _ => Some("")
//      case 2 =>
//        for {
//          resolver <- resolvers.lift(rowIndex)
//          index <- resolver.getIndex(project)
//        } yield {
//          val ts = index.getUpdateTimeStamp
//          if (ts == ResolverIndex.NO_TIMESTAMP)
//            SbtBundle.message("sbt.settings.resolvers.neverUpdated")
//          else if (ts == ResolverIndex.MAVEN_UNAVALIABLE)
//            SbtBundle.message("sbt.settings.resolvers.mavenUnavailable")
//          else
//            DateFormatUtil.formatDate(ts)
//        }
//
    }
    valueOpt.getOrElse("???")
  }
}
