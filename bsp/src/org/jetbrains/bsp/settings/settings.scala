package org.jetbrains.bsp.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components._
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings
import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.settings._
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil._
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil, ExternalSystemSettingsControl, ExternalSystemUiUtil, PaintAwarePanel}
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.{OptionTag, XCollection}
import org.jetbrains.bsp.settings.BspProjectSettings._
import org.jetbrains.bsp._
import org.jetbrains.plugins.scala.project.ProjectExt

import java.io.File
import java.nio.file.{Path, Paths}
import java.util
import javax.swing.JCheckBox
import scala.beans.BeanProperty

class BspProjectSettings extends ExternalProjectSettings {

  @BeanProperty
  var buildOnSave = false

  @BeanProperty
  var runPreImportTask = true

  @BeanProperty
  @OptionTag(converter = classOf[BspServerConfigConverter])
  var serverConfig: BspServerConfig = AutoConfig

  @BeanProperty
  @OptionTag(converter = classOf[PreImportConfigConverter])
  var preImportConfig: PreImportConfig = AutoPreImport

  override def setExternalProjectPath(externalProjectPath: String): Unit = {
    super.setExternalProjectPath(ExternalSystemApiUtil.toCanonicalPath(externalProjectPath))
  }

  override def clone(): BspProjectSettings = {
    val result = new BspProjectSettings
    copyTo(result)
    result.buildOnSave = buildOnSave
    result.runPreImportTask = runPreImportTask
    result.serverConfig = serverConfig
    result.preImportConfig = preImportConfig
    result
  }
}

object BspProjectSettings {

  /** A specific configuration to start and connect to a BSP server. */
  sealed abstract class BspServerConfig
  /** Choose BSP config automatically */
  case object AutoConfig extends BspServerConfig
  /** Bloop without preimport */
  case object BloopConfig extends BspServerConfig
  /** Use BSP config file to specify connection */
  case class BspConfigFile(path: Path) extends BspServerConfig

  /** A Task to run before connecting to BSP server and importing project.  */
  sealed abstract class PreImportConfig
  /** Do not run any PreImporter */
  case object NoPreImport extends PreImportConfig
  /** Attempt to choose pre-importer automatically */
  case object AutoPreImport extends PreImportConfig
  /** Preimport with Bloop from sbt project */
  case object BloopSbtPreImport extends PreImportConfig
  /** Preimport with BSP from Mill project */
  case object MillBspPreImport extends PreImportConfig

  class PreImportConfigConverter extends Converter[PreImportConfig] {
    override def fromString(value: String): PreImportConfig =
      value match {
        case "NoPreImport" => NoPreImport
        case "AutoPreImport" => AutoPreImport
        case "BloopBspPreImport" => BloopSbtPreImport
        case "MillBspPreImport" => MillBspPreImport
      }

    override def toString(value: PreImportConfig): String =
      value match {
        case NoPreImport => "NoPreImport"
        case AutoPreImport => "AutoPreImport"
        case BloopSbtPreImport => "BloopBspPreImport"
        case MillBspPreImport => "MillBspPreImport"
      }
  }

  class BspServerConfigConverter extends Converter[BspServerConfig] {
    private val configFile = "BspConfigFile:(?<path>.*)".r
    override def fromString(value: String): BspServerConfig = {
      value match {
        case "AutoConfig" => AutoConfig
        case "BloopConfig" => BloopConfig
        case configFile(path) => BspConfigFile(Paths.get(path))
      }
    }

    override def toString(value: BspServerConfig): String =
      value match {
        case AutoConfig => "AutoConfig"
        case BloopConfig => "BloopConfig"
        case BspConfigFile(path) => s"BspConfigFile:$path"
      }
  }
}

class BspProjectSettingsControl(settings: BspProjectSettings)
  extends AbstractExternalProjectSettingsControl[BspProjectSettings](null, settings) {

  @BeanProperty
  var buildOnSave = false

  @BeanProperty
  var runPreImportTask = true

  @BeanProperty
  var preImportConfig: PreImportConfig = AutoPreImport

  @BeanProperty
  var serverConfig: BspServerConfig = AutoConfig

  private val buildOnSaveCheckBox = new JCheckBox(BspBundle.message("bsp.protocol.build.automatically.on.file.save"))
  private val runPreImportTaskCheckBox = new JCheckBox(BspBundle.message("bsp.protocol.export.sbt.projects.to.bloop.before.import"))

  override def fillExtraControls(content: PaintAwarePanel, indentLevel: Int): Unit = {
    val fillLineConstraints = getFillLineConstraints(1)
    content.add(buildOnSaveCheckBox, fillLineConstraints)
    content.add(runPreImportTaskCheckBox, fillLineConstraints)
  }

  override def isExtraSettingModified: Boolean = {
    val initial = getInitialSettings
    buildOnSaveCheckBox.isSelected != initial.buildOnSave ||
      runPreImportTaskCheckBox.isSelected != initial.runPreImportTask
  }

  override def resetExtraSettings(isDefaultModuleCreation: Boolean): Unit = {
    val initial = getInitialSettings
    buildOnSaveCheckBox.setSelected(initial.buildOnSave)
    runPreImportTaskCheckBox.setSelected(initial.runPreImportTask)
  }

  override def applyExtraSettings(settings: BspProjectSettings): Unit = {
    settings.buildOnSave = buildOnSaveCheckBox.isSelected
    settings.runPreImportTask = runPreImportTaskCheckBox.isSelected
  }

  override def validate(settings: BspProjectSettings): Boolean = true

  override def updateInitialExtraSettings(): Unit = {
    applyExtraSettings(getInitialSettings)
  }

}


/** A dummy to satisfy interface constraints of ExternalSystem */
trait BspProjectSettingsListener extends ExternalSystemSettingsListener[BspProjectSettings] {
  def onBuildOnSaveChanged(buildOnSave: Boolean): Unit
  def onRunPreImportTaskChanged(runBloopInstall: Boolean): Unit
  def onPreImportConfigChanged(preImportConfig: PreImportConfig): Unit
  def onServerConfigChanged(serverConfig: BspServerConfig): Unit
}

class BspProjectSettingsListenerAdapter(listener: ExternalSystemSettingsListener[BspProjectSettings])
  extends DelegatingExternalSystemSettingsListener[BspProjectSettings](listener) with BspProjectSettingsListener {
  override def onBuildOnSaveChanged(buildOnSave: Boolean): Unit = {}
  override def onRunPreImportTaskChanged(runBloopInstall: Boolean): Unit = {}
  override def onPreImportConfigChanged(preImportConfig: PreImportConfig): Unit = {}
  override def onServerConfigChanged(serverConfig: BspServerConfig): Unit = {}
}

@State(
  name = "BspSettings",
  storages = Array(new Storage("bsp.xml"))
)
class BspSettings(project: Project)
  extends AbstractExternalSystemSettings[BspSettings, BspProjectSettings, BspProjectSettingsListener](BspSettings.BspTopic, project)
    with PersistentStateComponent[BspSettings.State]
{

  def getSystemSettings: BspSystemSettings = BspSystemSettings.getInstance

  override def subscribe(listener: ExternalSystemSettingsListener[BspProjectSettings]): Unit = {
    val adapter = new BspProjectSettingsListenerAdapter(listener)
    getProject.getMessageBus.connect(getProject.unloadAwareDisposable).subscribe(BspSettings.BspTopic, adapter)
  }

  override def copyExtraSettingsFrom(settings: BspSettings): Unit = {}

  override def checkSettings(old: BspProjectSettings, current: BspProjectSettings): Unit = {
    if (old.buildOnSave != current.buildOnSave)
      getPublisher.onBuildOnSaveChanged(current.buildOnSave)
    if (old.runPreImportTask != current.runPreImportTask)
      getPublisher.onRunPreImportTaskChanged(current.runPreImportTask)
    if (old.preImportConfig != current.preImportConfig)
      getPublisher.onPreImportConfigChanged(current.preImportConfig)
    if (old.serverConfig != current.serverConfig)
      getPublisher.onServerConfigChanged(current.serverConfig)
  }

  override def getState: BspSettings.State = {
    val state = new BspSettings.State
    fillState(state)
    state
  }

  override def loadState(state: BspSettings.State): Unit = {
    super[AbstractExternalSystemSettings].loadState(state)
  }
}

object BspSettings {

  class State extends AbstractExternalSystemSettings.State[BspProjectSettings] {

    private val projectSettings = new util.TreeSet[BspProjectSettings]

    @XCollection(style = XCollection.Style.v1, elementTypes = Array(classOf[BspProjectSettings]))
    override def getLinkedExternalProjectsSettings: util.Set[BspProjectSettings] = projectSettings
    override def setLinkedExternalProjectsSettings(settings: util.Set[BspProjectSettings]): Unit =
      projectSettings.addAll(settings)
  }

  def getInstance(project: Project): BspSettings = project.getService(classOf[BspSettings])

  val BspTopic: Topic[BspProjectSettingsListener] = new Topic(BspBundle.message("bsp.protocol.specific.settings"), classOf[BspProjectSettingsListener])
}


@State(
  name = "BspSystemSettings",
  storages = Array(new Storage("bsp.settings.xml")),
  reportStatistic = true
)
class BspSystemSettings extends PersistentStateComponent[BspSystemSettings.State] {

  @BeanProperty
  var myState: BspSystemSettings.State = new BspSystemSettings.State

  override def getState: BspSystemSettings.State = myState

  override def loadState(state: BspSystemSettings.State): Unit = {
    myState = state
  }
}

object BspSystemSettings {
  def getInstance: BspSystemSettings = ApplicationManager.getApplication.getService(classOf[BspSystemSettings])

  class State {
    @BeanProperty
    var traceBsp: Boolean = false
  }
}


@State(
  name = "BspLocalSettings",
  storages = Array(new Storage(StoragePathMacros.WORKSPACE_FILE))
)
class BspLocalSettings(project: Project)
  extends AbstractExternalSystemLocalSettings[BspLocalSettingsState](BSP.ProjectSystemId, project)
    with PersistentStateComponent[BspLocalSettingsState] {

  override def loadState(state: BspLocalSettingsState): Unit =
    super[AbstractExternalSystemLocalSettings].loadState(state)
}

object BspLocalSettings {
  def getInstance(project: Project): BspLocalSettings = project.getService(classOf[BspLocalSettings])
}

class BspLocalSettingsState extends AbstractExternalSystemLocalSettings.State

class BspExecutionSettings(val basePath: File,
                           val traceBsp: Boolean,
                           val runPreImportTask: Boolean,
                           val preImportTask: PreImportConfig,
                           val config: BspServerConfig
                          ) extends ExternalSystemExecutionSettings

object BspExecutionSettings {

  def executionSettingsFor(project: Project, basePath: File): BspExecutionSettings = {
    if (project == null) executionSettingsFor(basePath)
    val bspSettings = BspSettings.getInstance(project)
    val bspTraceLog = BspSystemSettings.getInstance.getState.traceBsp
    val linkedSettings = Option(bspSettings.getLinkedProjectSettings(basePath.getAbsolutePath))
    val runPreImportTask = linkedSettings.forall(_.runPreImportTask)
    val preImportConfig = linkedSettings.map(_.preImportConfig).getOrElse(AutoPreImport)
    val serverConfig = linkedSettings.map(_.serverConfig).getOrElse(AutoConfig)

    new BspExecutionSettings(basePath, bspTraceLog, runPreImportTask, preImportConfig, serverConfig)
  }

  def executionSettingsFor(basePath: File): BspExecutionSettings = {
    val systemSettings = BspSystemSettings.getInstance
    val defaultProjectSettings = new BspProjectSettings
    new BspExecutionSettings(
      basePath, systemSettings.getState.traceBsp, defaultProjectSettings.runPreImportTask, AutoPreImport, AutoConfig)
  }
}

class BspSystemSettingsControl(settings: BspSettings) extends ExternalSystemSettingsControl[BspSettings] {

  private val pane = new BspSystemSettingsPane
  private val systemSettings = settings.getSystemSettings

  override def fillUi(canvas: PaintAwarePanel, indentLevel: Int): Unit = {
    canvas.add(pane.content, ExternalSystemUiUtil.getFillLineConstraints(indentLevel))
  }

  override def showUi(show: Boolean): Unit ={
    pane.content.setVisible(show)
  }

  override def reset(): Unit = {
    pane.bspTraceCheckbox.setSelected(systemSettings.getState.traceBsp)
  }

  override def isModified: Boolean =
    pane.bspTraceCheckbox.isSelected != systemSettings.getState.traceBsp

  override def apply(settings: BspSettings): Unit = {
    systemSettings.getState.traceBsp = pane.bspTraceCheckbox.isSelected
  }

  override def validate(settings: BspSettings): Boolean =
    true

  override def disposeUIResources(): Unit = {}
}
