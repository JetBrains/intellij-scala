package org.jetbrains.bsp.project.importing

import java.awt.BorderLayout
import java.io.File

import ch.epfl.scala.bsp4j.BspConnectionDetails
import com.intellij.ide.util.projectWizard.{ModuleWizardStep, WizardContext}
import com.intellij.openapi.progress.{ProgressIndicator, Task}
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBList
import javax.swing.{DefaultListModel, JComponent, JPanel, ListSelectionModel}
import org.jetbrains.bsp.BspUtil
import org.jetbrains.bsp.project.importing.BspSetupConfigStep.ConfigSetupTask
import org.jetbrains.bsp.project.importing.setup.{BspConfigSetup, MillConfigSetup, NoConfigSetup, SbtConfigSetup}
import org.jetbrains.bsp.project.importing.bspConfigSteps._
import org.jetbrains.bsp.protocol.BspConnectionConfig
import org.jetbrains.bsp.settings.BspProjectSettings.{AutoConfig, AutoPreImport, BloopConfig, BloopSbtPreImport, BspConfigFile, NoPreImport}
import org.jetbrains.plugins.scala.build.IndicatorReporter
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.SbtUtil._
import org.jetbrains.sbt.project.{MillProjectImportProvider, SbtProjectImportProvider}

object bspConfigSteps {

  sealed private[importing] abstract class ConfigSetup
  case object NoSetup extends ConfigSetup
  case object SbtSetup extends ConfigSetup
  case object BloopSetup extends ConfigSetup
  case object BloopSbtSetup extends ConfigSetup
  case object MillSetup extends ConfigSetup

  private[importing] def configChoiceName(configs: ConfigSetup) = configs match {
    case NoSetup => "do net setup BSP configuration"
    case SbtSetup => "create sbt configuration"
    case BloopSetup => "use existing Bloop configuration"
    case BloopSbtSetup => "use sbt with Bloop"
    case MillSetup => "create Mill configuration"
  }

  private[importing] def configName(config: BspConnectionDetails) =
    s"${config.getName} ${config.getVersion}"

  def configSetupChoices(workspace: File): List[ConfigSetup] = {
    val workspaceConfigs = workspaceSetupChoices(workspace)
    if (workspaceConfigs.size == 1) List(NoSetup)
    else if (workspaceConfigs.nonEmpty) workspaceConfigs
    else List(NoSetup)
  }

  def configureBuilder(builder: BspProjectImportBuilder, workspace: File, configSetup: ConfigSetup): BspConfigSetup = {
    val workspaceBspConfigs = BspConnectionConfig.workspaceBspConfigs(workspace)
    if (workspaceBspConfigs.size == 1) {
      builder.setPreImportConfig(NoPreImport)
      builder.setServerConfig(BspConfigFile(workspaceBspConfigs.head._1.toPath))
      new NoConfigSetup
    } else configSetup match {
        case bspConfigSteps.NoSetup =>
          builder.setPreImportConfig(AutoPreImport)
          builder.setServerConfig(AutoConfig)
          new NoConfigSetup
        case bspConfigSteps.BloopSetup =>
          builder.setPreImportConfig(NoPreImport)
          builder.setServerConfig(BloopConfig)
          new NoConfigSetup
        case bspConfigSteps.BloopSbtSetup =>
          builder.setPreImportConfig(BloopSbtPreImport)
          builder.setServerConfig(BloopConfig)
          new NoConfigSetup
        case bspConfigSteps.SbtSetup =>
          builder.setPreImportConfig(NoPreImport)
          // server config to be set in next step
          SbtConfigSetup(workspace)
        case bspConfigSteps.MillSetup =>
          builder.setPreImportConfig(NoPreImport)
          MillConfigSetup(workspace)
      }
  }

  def workspaceSetupChoices(workspace: File): List[ConfigSetup] = {

    val vfile = LocalFileSystem.getInstance().findFileByIoFile(workspace)

    val sbtChoice = if (SbtProjectImportProvider.canImport(vfile)) {
      val sbtVersion = Version(detectSbtVersion(workspace, getDefaultLauncher))
      if (sbtVersion.major(2) >= Version("1.4")) {
        // sbt >= 1.4 : user choose: bloop or sbt
        List(BloopSbtSetup, SbtSetup)
      } else {
        List(BloopSbtSetup)
      }
    } else Nil

    val millChoice =
      if (MillProjectImportProvider.canImport(vfile)) List(MillSetup)
      else Nil

    val bloopChoice =
      if (BspUtil.bloopConfigDir(workspace).isDefined) List(BloopSetup)
      else Nil


    (sbtChoice ++ millChoice ++ bloopChoice).distinct
  }
}

class BspSetupConfigStep(context: WizardContext, builder: BspProjectImportBuilder)
  extends ModuleWizardStep {

  private var runSetupTask: BspConfigSetup = new NoConfigSetup

  private val workspace = context.getProjectDirectory.toFile

  private val workspaceBspConfigs = BspConnectionConfig.workspaceBspConfigs(workspace)
  private lazy val workspaceSetupConfigs: List[ConfigSetup] = workspaceSetupChoices(workspace)

  private val configSetupChoices = {
    if (workspaceBspConfigs.size == 1) List(NoSetup)
    else if (workspaceSetupConfigs.nonEmpty) workspaceSetupConfigs
    else List(NoSetup)
  }

  private val myComponent = new JPanel(new BorderLayout)
  private val chooseBspSetup = new JBList[String]()
  private val chooseBspSetupModel = new DefaultListModel[String]

  myComponent.add(chooseBspSetup)
  chooseBspSetup.setModel(chooseBspSetupModel)
  chooseBspSetup.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)


  override def getComponent: JComponent = myComponent

  override def validate(): Boolean = {
    workspaceBspConfigs.nonEmpty ||
      configSetupChoices.size == 1 ||
      chooseBspSetup.getSelectedIndex >= 0
  }

  override def updateStep(): Unit = {
    chooseBspSetupModel.clear()
    configSetupChoices
      .map(configChoiceName)
      .foreach(choice => chooseBspSetupModel.addElement(choice))
  }

  override def updateDataModel(): Unit = {

    val configIndex =
      if (configSetupChoices.size == 1) 0
      else chooseBspSetup.getSelectedIndex

    runSetupTask = configureBuilder(builder, workspace, configSetupChoices(configIndex))
  }

  override def isStepVisible: Boolean = {
    builder.preImportConfig == AutoPreImport &&
      configSetupChoices.size > 1 &&
      workspaceBspConfigs.isEmpty
  }

  override def onWizardFinished(): Unit = {
    // TODO this spawns an indicator window which is not nice.
    // show a live log in the window or something?
    val task = new ConfigSetupTask(runSetupTask)
    task.queue()
  }

}
object BspSetupConfigStep {

  private class ConfigSetupTask(setup: BspConfigSetup)
    extends Task.Modal(null, "Setting up BSP configuration", true) {

    override def run(indicator: ProgressIndicator): Unit = {
      val reporter = new IndicatorReporter(indicator)
      setup.run(reporter)
    }

    override def onCancel(): Unit =
      setup.cancel()
  }

}

class BspChooseConfigStep(context: WizardContext, builder: BspProjectImportBuilder)
  extends ModuleWizardStep {

  private val workspace = context.getProjectDirectory.toFile
  private val myComponent = new JPanel(new BorderLayout)
  private val chooseBspConfig = new JBList[String]()
  private val chooseBspSetupModel = new DefaultListModel[String]

  private def bspConfigs = BspConnectionConfig.allBspConfigs(workspace)

  myComponent.add(chooseBspConfig)
  chooseBspConfig.setModel(chooseBspSetupModel)
  chooseBspConfig.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)

  override def getComponent: JComponent = myComponent

  override def validate(): Boolean = {
    // config already chosen in previous step
    val alreadySet = builder.serverConfig != AutoConfig

    // there should be at least one config at this point
    val configsExist = !chooseBspConfig.isEmpty
    val configSelected = (chooseBspConfig.getItemsCount == 1 || chooseBspConfig.getSelectedIndex >= 0)

    alreadySet || (configsExist && configSelected)
  }

  override def updateStep(): Unit = {
    chooseBspSetupModel.clear()
    bspConfigs
      .map { case (_,details) => configName(details) }
      .foreach(chooseBspSetupModel.addElement)
  }

  override def updateDataModel(): Unit = {
    val configIndex =
      if (chooseBspConfig.getItemsCount == 1) 0
      else chooseBspConfig.getSelectedIndex

    if (configIndex >= 0) {
      val (file,_) = bspConfigs(configIndex)
      val config = BspConfigFile(file.toPath)
      builder.setServerConfig(config)
    }
  }

  override def onWizardFinished(): Unit = {
    updateStep()
    if (builder.serverConfig == AutoConfig && chooseBspConfig.getItemsCount == 1) {
      val (file,_) = bspConfigs.head
      val config = BspConfigFile(file.toPath)
      builder.setServerConfig(config)
    }
  }

  override def isStepVisible: Boolean = {
    updateStep()
    builder.serverConfig == AutoConfig && chooseBspConfig.getItemsCount > 1
  }
}
