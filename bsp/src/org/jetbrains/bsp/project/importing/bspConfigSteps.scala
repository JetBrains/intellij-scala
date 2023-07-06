package org.jetbrains.bsp.project.importing

import ch.epfl.scala.bsp4j.BspConnectionDetails
import com.intellij.ide.util.projectWizard.{ModuleWizardStep, WizardContext}
import com.intellij.openapi.progress.{ProgressIndicator, Task}
import org.jetbrains.plugins.scala.project.external.SdkUtils
import com.intellij.openapi.projectRoots.{JavaSdk, Sdk, SdkTypeId}
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.{Condition, NlsContexts}
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBList
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager, Spacer}
import com.intellij.util.ui.{JBUI, UI}
import org.jetbrains.annotations.Nls
import org.jetbrains.bsp.project.importing.BspSetupConfigStep.BspConfigSetupTask
import org.jetbrains.bsp.project.importing.bspConfigSteps._
import org.jetbrains.bsp.project.importing.setup.{BspConfigSetup, FastpassConfigSetup, NoConfigSetup, SbtConfigSetup}
import org.jetbrains.bsp.protocol.BspConnectionConfig
import org.jetbrains.bsp.settings.BspProjectSettings._
import org.jetbrains.bsp.{BspBundle, BspUtil}
import org.jetbrains.plugins.scala.build.IndicatorReporter
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.SbtUtil._
import org.jetbrains.sbt.project.SbtProjectImportProvider

import java.awt.{GridBagConstraints, GridBagLayout}
import java.io.File
import java.nio.file.Path
import javax.swing.{DefaultListModel, JComponent, JLabel, JPanel, ListSelectionModel}
import scala.annotation.nowarn

object bspConfigSteps {

  sealed private[importing] abstract class ConfigSetup
  case object NoSetup extends ConfigSetup
  case object SbtSetup extends ConfigSetup
  case object BloopSetup extends ConfigSetup
  case object BloopSbtSetup extends ConfigSetup
  case object MillSetup extends ConfigSetup
  case object FastpassSetup extends ConfigSetup

  private[importing] def configChoiceName(configs: ConfigSetup) = configs match {
    case NoSetup => BspBundle.message("bsp.config.steps.choice.no.setup")
    case SbtSetup => BspBundle.message("bsp.config.steps.choice.sbt")
    case BloopSetup => BspBundle.message("bsp.config.steps.choice.bloop")
    case BloopSbtSetup => BspBundle.message("bsp.config.steps.choice.sbt.with.bloop")
    case MillSetup => BspBundle.message("bsp.config.steps.choice.mill")
    case FastpassSetup => BspBundle.message("bsp.config.steps.choice.fastpass")
  }

  private[importing] def configName(config: BspConnectionDetails) =
    s"${config.getName} ${config.getVersion}"

  private[importing] def withTooltip(component: JComponent, @Nls tooltip: String) =
    UI.PanelFactory.panel(component).withTooltip(tooltip).createPanel(): @nowarn("cat=deprecation")

  private[importing] def addTitledComponent(parent: JComponent, title: JComponent, component: JComponent, row: Int, shouldAddSpacer: Boolean): Int = {
    val titleConstraints = new GridConstraints()
    titleConstraints.setRow(row)
    titleConstraints.setFill(GridConstraints.FILL_HORIZONTAL)
    parent.add(title, titleConstraints)

    val listConstraints = new GridConstraints()
    listConstraints.setRow(row + 1)
    listConstraints.setFill(GridConstraints.FILL_BOTH)
    listConstraints.setIndent(1)
    parent.add(component, listConstraints)
    if (shouldAddSpacer) addSpacer(parent, row + 2)
    else row + 2
  }

  private[importing] def addSpacer(parent: JComponent, row: Int): Int = {
    val spacer = new Spacer()
    val spacerConstraints = new GridConstraints()
    spacerConstraints.setRow(row)
    spacerConstraints.setVSizePolicy(GridConstraints.SIZEPOLICY_WANT_GROW | GridConstraints.SIZEPOLICY_CAN_GROW)
    parent.add(spacer, spacerConstraints)
    row +1
  }

  def configSetupChoices(workspace: File): List[ConfigSetup] = {
    val workspaceConfigs = workspaceSetupChoices(workspace)
    if (workspaceConfigs.nonEmpty) workspaceConfigs
    else List(NoSetup)
  }

  def configureBuilder(
    jdk: Sdk,
    builder: BspProjectImportBuilder,
    workspace: File,
    configSetup: ConfigSetup
  ): BspConfigSetup = {
    val BuilderConfigurationParameters(
      setup: BspConfigSetup,
      preImportConfig: Option[PreImportConfig],
      serverConfig: Option[BspServerConfig],
      externalBspWorkspace: Option[Path]
    ) = getBuilderConfigurationParameters(jdk, workspace, configSetup)

    preImportConfig.foreach(builder.setPreImportConfig)
    serverConfig.foreach(builder.setServerConfig)
    externalBspWorkspace.foreach(builder.setExternalBspWorkspace)

    setup
  }

  case class BuilderConfigurationParameters(
    bspConfigSetup: BspConfigSetup,
    preImportConfig: Option[PreImportConfig],
    serverConfig: Option[BspServerConfig],
    externalBspWorkspace: Option[Path]
  )

  def getBuilderConfigurationParameters(
    jdk: Sdk,
    workspace: File,
    configSetup: ConfigSetup
  ): BuilderConfigurationParameters = {
    val workspaceBspConfigs = BspConnectionConfig.workspaceBspConfigs(workspace)

    val tuple = if (workspaceBspConfigs.size == 1)
      (NoConfigSetup, Some(NoPreImport), Some(BspConfigFile(workspaceBspConfigs.head._1.toPath)), None)
    else configSetup match {
      case bspConfigSteps.NoSetup =>
        (NoConfigSetup, Some(AutoPreImport), Some(AutoConfig), None)
      case bspConfigSteps.BloopSetup =>
        (NoConfigSetup, Some(NoPreImport), Some(BloopConfig), None)
      case bspConfigSteps.BloopSbtSetup =>
        (NoConfigSetup, Some(BloopSbtPreImport), Some(BloopConfig), None)
      case bspConfigSteps.SbtSetup =>
        (SbtConfigSetup(workspace, jdk), Some(NoPreImport), None, None) // server config to be set in next step
      case bspConfigSteps.MillSetup =>
        (NoConfigSetup, Some(MillBspPreImport), Some(AutoConfig), None)
      case bspConfigSteps.FastpassSetup =>
        val bspWorkspace = FastpassConfigSetup.computeBspWorkspace(workspace)
        val configSetup: BspConfigSetup = FastpassConfigSetup.create(workspace).fold(throw _, identity)
        (configSetup, Some(NoPreImport), None, Some(bspWorkspace))
    }
    BuilderConfigurationParameters.tupled.apply(tuple)
  }

  def workspaceSetupChoices(workspace: File): List[ConfigSetup] = {

    val vfile = LocalFileSystem.getInstance().findFileByIoFile(workspace)

    val sbtChoice = if (SbtProjectImportProvider.canImport(vfile)) {
      val sbtVersion = Version(detectSbtVersion(workspace, getDefaultLauncher))
      if (sbtVersion.major(2) >= Version("1.4")) {
        // sbt >= 1.4 : user choose: bloop or sbt
        List(SbtSetup, BloopSbtSetup)
      } else {
        List(BloopSbtSetup)
      }
    } else Nil

    val millChoice =
      if (MillProjectImportProvider.canImport(vfile.toNioPath.toFile)) List(MillSetup)
      else Nil

    val bloopChoice =
      if (BspUtil.bloopConfigDir(workspace).isDefined) List(BloopSetup)
      else Nil

    val fastpassChoice = if(FastpassProjectImportProvider.canImport(vfile))
      List(FastpassSetup)
    else
      Nil


    (sbtChoice ++ millChoice ++ bloopChoice ++ fastpassChoice).distinct
  }
}

class BspSetupConfigStep(wizardContext: WizardContext, builder: BspProjectImportBuilder, setupTaskWorkspace: File)
  extends ModuleWizardStep {

  private var runSetupTask: BspConfigSetup = NoConfigSetup

  private val workspaceBspConfigs = BspConnectionConfig.workspaceBspConfigs(setupTaskWorkspace)
  private lazy val workspaceSetupConfigs: List[ConfigSetup] = workspaceSetupChoices(setupTaskWorkspace)
  private val existingJdk = SdkUtils.getSdkForProject(Option(wizardContext.getProject))

  private val configSetupChoices: List[ConfigSetup] = {
    if (workspaceBspConfigs.size == 1) List(NoSetup)
    else if (workspaceSetupConfigs.nonEmpty) workspaceSetupConfigs
    else List(NoSetup)
  }

  private val bspSetupConfigStepUi = new BspSetupConfigStepUi(BspBundle.message("bsp.config.steps.setup.config.choose.tool"), configSetupChoices, existingJdk.isEmpty)

  override def getComponent: JComponent = bspSetupConfigStepUi.mainComponent

  override def getPreferredFocusedComponent: JComponent = bspSetupConfigStepUi.chooseBspSetupList

  override def validate(): Boolean = {
    (workspaceBspConfigs.nonEmpty ||
      configSetupChoices.size == 1 ||
      bspSetupConfigStepUi.chooseBspSetupList.getSelectedIndex >= 0 ) &&
      bspSetupConfigStepUi.isJdkSelected()
  }

  override def updateStep(): Unit = {
    bspSetupConfigStepUi.updateChooseBspSetupComponent(configSetupChoices)
  }

  override def updateDataModel(): Unit = {

    val configIndex =
      if (configSetupChoices.size == 1) 0
      else bspSetupConfigStepUi.chooseBspSetupList.getSelectedIndex

    runSetupTask = existingJdk.orElse(Option(bspSetupConfigStepUi.jdkComboBox.getSelectedJdk)) match {
      case Some(jdk) if configSetupChoices.size > configIndex && configIndex >= 0 => configureBuilder(jdk, builder, setupTaskWorkspace, configSetupChoices(configIndex))
      case _ => NoConfigSetup
    }

  }

  override def isStepVisible: Boolean = {
    builder.preImportConfig == AutoPreImport &&
      (configSetupChoices.size > 1 || existingJdk.isEmpty) &&
      workspaceBspConfigs.isEmpty
  }

  override def onWizardFinished(): Unit = {
    // TODO this spawns an indicator window which is not nice.
    // show a live log in the window or something?
    if (wizardContext.getProjectBuilder.isInstanceOf[BspProjectImportBuilder]) {
      Option(bspSetupConfigStepUi.jdkComboBox.getSelectedJdk).foreach(SdkUtils.addSdkIfNotExists)
      updateDataModel() // without it runSetupTask is null
      builder.prepare(wizardContext)
      //this will use DefaultProject, which will lead to exception IDEA-289729
      //builder.ensureProjectIsDefined(wizardContext)
      val task = new BspConfigSetupTask(runSetupTask)
      task.queue()
    }
  }

}
object BspSetupConfigStep {

  private[importing] class BspConfigSetupTask(setup: BspConfigSetup)
    extends Task.Modal(null, BspBundle.message("bsp.config.steps.setup.config.task.title"), true) {

    override def run(indicator: ProgressIndicator): Unit = {
      val reporter = new IndicatorReporter(indicator)
      setup.run(reporter)
    }

    override def onCancel(): Unit =
      setup.cancel()
  }
}

final class BspSetupConfigStepUi(
  @NlsContexts.Separator title: String,
  configSetups: Seq[ConfigSetup],
  showJdkComboBox: Boolean
) {

  val mainComponent: JPanel = {
    val manager = new GridLayoutManager(5, 1)
    manager.setSameSizeHorizontally(false)
    new JPanel(manager)
  }
  private val chooseBspSetupModel = new DefaultListModel[String]
  val chooseBspSetupList = new JBList[String](chooseBspSetupModel)
  private val model = new ProjectSdksModel()

  val jdkComboBox: JdkComboBox = {
    model.reset(null)
    val jdkFilter: Condition[SdkTypeId] = (sdk: SdkTypeId) => sdk == JavaSdk.getInstance()
    new JdkComboBox(null, model, jdkFilter, null, jdkFilter, null)
  }

  locally {
    var row = 0
    val titleWithTip = withTooltip(new TitledSeparator(title), BspBundle.message("bsp.config.steps.setup.config.choose.tool.tooltip"))
    chooseBspSetupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    row = addTitledComponent(mainComponent, titleWithTip, chooseBspSetupList, row, shouldAddSpacer = false)

    if (showJdkComboBox) {
      val panelForComboBox = new JPanel(new GridBagLayout)
      val label = new JLabel("JDK")
      panelForComboBox.add(label, new GridBagConstraints(0, 1, 1, 1, 0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insetsTop(8), 0, 0))
      panelForComboBox.add(jdkComboBox, new GridBagConstraints(1, 1, 1, 1, 1.0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, JBUI.insets(2, 10, 10, 0), 0, 0))
      val jdkTitleWithTip = withTooltip(new TitledSeparator(BspBundle.message("bsp.config.steps.setup.config.choose.jdk")), BspBundle.message("bsp.config.steps.setup.config.choose.jdk.tooltip"))
      addTitledComponent(mainComponent, jdkTitleWithTip, panelForComboBox, row, shouldAddSpacer = true)
    } else addSpacer(mainComponent, row)
  }

  def selectedConfigSetup: ConfigSetup =
    configSetups(chooseBspSetupList.getSelectedIndex)

  def updateChooseBspSetupComponent(configSetupChoices: Seq[ConfigSetup]): Unit = {
    val setupChoicesStrings = getConfigSetupChoicesStrings(configSetupChoices)
    chooseBspSetupModel.clear()
    setupChoicesStrings.foreach(chooseBspSetupModel.addElement)
    chooseBspSetupList.setSelectedIndex(0)
  }

  private def getConfigSetupChoicesStrings(configSetupChoices: Seq[ConfigSetup]): Seq[String] = {
    val recommendedSuffix = BspBundle.message("bsp.config.steps.choose.config.recommended.suffix")
    val configChoiceName = configSetupChoices.map(bspConfigSteps.configChoiceName)
    configChoiceName match {
      case Seq(head, tail@_*) =>
        s"$head ($recommendedSuffix)" +: tail
      case Nil =>
        Nil
    }
  }

  def isJdkSelected() =
    if (showJdkComboBox) jdkComboBox.getSelectedJdk != null
    else true
}

class BspChooseConfigStep(context: WizardContext, builder: BspProjectImportBuilder)
  extends ModuleWizardStep {

  private val myComponent = {
    val manager = new GridLayoutManager(5, 1)
    manager.setSameSizeHorizontally(false)
    new JPanel(manager)
  }
  private val chooseBspConfig = new JBList[String]()
  private val chooseBspSetupModel = new DefaultListModel[String]
  chooseBspConfig.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
  chooseBspConfig.setModel(chooseBspSetupModel)

  private def bspConfigs = BspConnectionConfig.allBspConfigs(context.getProjectDirectory.toFile)

  {
    val chooseSetupTitle = new TitledSeparator(BspBundle.message("bsp.config.steps.choose.config.title"))
    val titleWithTip = withTooltip(chooseSetupTitle, BspBundle.message("bsp.config.steps.choose.config.title.tooltip"))
    addTitledComponent(myComponent, titleWithTip, chooseBspConfig, 0, shouldAddSpacer = true)
  }

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
