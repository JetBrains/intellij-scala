package org.jetbrains.sbt.project.module

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.{ModuleConfigurationState, ModuleElementsEditor}
import org.jetbrains.plugins.scala.help.ScalaWebHelpProvider
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.resolvers.SbtResolver
import org.jetbrains.sbt.settings.SbtSettings

import javax.swing.JPanel
import javax.swing.table.AbstractTableModel
import scala.jdk.CollectionConverters._

private class SbtModuleSettingsEditor(state: ModuleConfigurationState) extends ModuleElementsEditor(state) {

  import SbtModule._

  private val myForm = new SbtModuleSettingsForm
  private val resolvers = Resolvers(getModel.getModule).toSeq

  override def getDisplayName: String = SbtBundle.message("sbt.settings.sbtModuleSettings")

  override def getHelpTopic: String =
    ScalaWebHelpProvider.HelpPrefix + "sbt-support.html"

  override def saveData(): Unit = {}

  override def createComponentImpl(): JPanel = myForm.getMainPanel

  override def reset(): Unit = {
    val module = getModel.getModule
    val sbtProjectSettings = SbtSettings.getInstance(state.getProject).getLinkedProjectSettings(module)

    val sbtVersion = sbtProjectSettings.map(_.sbtVersion).getOrElse(SbtBundle.message("sbt.settings.sbtVersionNotDetected"))
    val imports = Imports(module).asJava
    val resolversModel = new ResolversModel(resolvers, state.getProject)

    myForm.reset(sbtVersion, imports, resolversModel)
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
