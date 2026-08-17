package org.jetbrains.bsp.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components._
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings
import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.settings._
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil._
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil, ExternalSystemSettingsControl, ExternalSystemUiUtil, PaintAwarePanel}
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import com.intellij.util.ui.UI
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.{OptionTag, XCollection}
import com.intellij.ui.TitledSeparator
import org.jetbrains.annotations.Nullable
import org.jetbrains.bsp._
import org.jetbrains.bsp.settings.BspProjectSettings._
import org.jetbrains.bsp.settings.PreImportConfig.AutoPreImport
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.{Path, Paths}
import java.util
import javax.swing.JCheckBox
import scala.annotation.nowarn
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
  var preImportConfig: PreImportConfig = AutoPreImport

  /** Whether the Scala plugin generated the BSP connection file during initial import */
  @BeanProperty
  var bspConfigGenerated: Boolean = false

  /**
   * Hash of the BSP connection files under the .bsp directory
   *
   * @see [[org.jetbrains.bsp.protocol.BspConnectionConfig.workspaceBspConfigsHash]]
   */
  @BeanProperty
  @Nullable
  var connectionFileHash: Integer = null

  /**
   * Whether to automatically regenerate the BSP connection file before the BSP server starts.
   * This is only applied to Scala CLI or Mill projects.
   */
  @BeanProperty
  var autoRegenerateBspConfigOnServerStartup = false

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
    result.bspConfigGenerated = bspConfigGenerated
    result.connectionFileHash = connectionFileHash
    result.autoRegenerateBspConfigOnServerStartup = autoRegenerateBspConfigOnServerStartup
    result
  }
}

object BspProjectSettings {

  /**
   * A specific configuration to start and connect to a BSP server.
   *
   * '''Important:''' this class is used as a type for one of the fields inside [[BspProjectSettings]] and is serialized.
   * By default, the IntelliJ mechanism for checking whether the new value of [[BspProjectSettings.serverConfig]]
   * is different from the default, and consequently whether it should be serialized or not, relies on comparing
   * the fields inside the class. Since this class has no fields, e.g., `AutoConfig` and `BloopConfig` were considered
   * equal (see [[https://youtrack.jetbrains.com/issue/IJPL-231922]]).
   *
   * To address this, a custom `equals` method is implemented inside this class. It switches the comparison
   * from fields-based to `equals`-method-based, ensuring the [[BspProjectSettings.serverConfig]] is properly serialized when required.
   *
   * This is just a workaround. If the ADT has no fields, like in
   * [[org.jetbrains.bsp.settings.PreImportConfig]], the simplest solution is to use a Java enum.
   *
   *   - When adding new subtypes ensure the `equals`/`hashCode` methods are updated and work correctly.
   *   - '''In case of any changes inside [[BspServerConfig]] and its subtypes adjust tests in [[org.jetbrains.bsp.settings.BspProjectSettingsTest]]'''
   */
  sealed abstract class BspServerConfig {
    override def equals(obj: Any): Boolean = {
      if (!obj.isInstanceOf[BspServerConfig] || this.getClass != obj.getClass) return false

      if (this.isInstanceOf[BspConfigFile]) {
        val _this = this.asInstanceOf[BspConfigFile]
        val other = obj.asInstanceOf[BspConfigFile]
        _this.path.toAbsolutePath.normalize() == other.path.toAbsolutePath.normalize()
      } else {
        true // case objects with same class are equal
      }
    }

    override def hashCode(): Int = this match {
      case AutoConfig => "AutoConfig".hashCode
      case BloopConfig => "BloopConfig".hashCode
      case BspConfigFile(path) => 31 * getClass.hashCode() + path.toAbsolutePath.normalize().hashCode()
    }
  }
  /** Choose BSP config automatically */
  case object AutoConfig extends BspServerConfig
  /** Bloop without preimport */
  case object BloopConfig extends BspServerConfig
  /** Use BSP config file to specify connection */
  case class BspConfigFile(path: Path) extends BspServerConfig

  private class BspServerConfigConverter extends Converter[BspServerConfig] {
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

  @BeanProperty
  var autoRegenerateBspConfigOnServerStartup = false

  @BeanProperty
  var bspConfigGenerated: Boolean = false

  @BeanProperty
  @Nullable
  var connectionFileHash: Integer = null

  private val buildOnSaveCheckBox = new JCheckBox(BspBundle.message("bsp.protocol.build.automatically.on.file.save"))
  private val runPreImportTaskCheckBox = new JCheckBox(BspBundle.message("bsp.protocol.export.sbt.projects.to.bloop.before.import"))
  private val autoRegenerateBspConfigCheckBox = new JCheckBox(BspBundle.message("bsp.protocol.auto.generate.config"))

  override def fillExtraControls(content: PaintAwarePanel, indentLevel: Int): Unit = {
    val fillLineConstraints = getFillLineConstraints(1)
    content.add(buildOnSaveCheckBox, fillLineConstraints)

    content.add(new TitledSeparator(BspBundle.message("bsp.protocol.sbt.project")), fillLineConstraints)
    content.add(runPreImportTaskCheckBox, fillLineConstraints)

    content.add(new TitledSeparator(BspBundle.message("bsp.protocol.scala.cli.mill.project")), fillLineConstraints)

    val panelBuilder = UI.PanelFactory.panel(autoRegenerateBspConfigCheckBox): @nowarn("cat=deprecation")
    val panelBuilderWithTooltip = panelBuilder.withTooltip(BspBundle.message("bsp.protocol.auto.generate.config.tooltip")): @nowarn("cat=deprecation")
    val panel = panelBuilderWithTooltip.createPanel()

    content.add(panel, fillLineConstraints)
  }

  override def isExtraSettingModified: Boolean = {
    val initial = getInitialSettings
    buildOnSaveCheckBox.isSelected != initial.buildOnSave ||
      runPreImportTaskCheckBox.isSelected != initial.runPreImportTask ||
      autoRegenerateBspConfigCheckBox.isSelected != initial.autoRegenerateBspConfigOnServerStartup
  }

  override def resetExtraSettings(isDefaultModuleCreation: Boolean): Unit = {
    val initial = getInitialSettings
    buildOnSaveCheckBox.setSelected(initial.buildOnSave)
    runPreImportTaskCheckBox.setSelected(initial.runPreImportTask)
    autoRegenerateBspConfigCheckBox.setSelected(initial.autoRegenerateBspConfigOnServerStartup)
    // It's required, even for settings that don't have corresponding checkboxes,
    // to preserve the existing values in the project settings instead of using defaults.
    preImportConfig = initial.preImportConfig
    serverConfig = initial.serverConfig
    bspConfigGenerated = initial.bspConfigGenerated
    connectionFileHash = initial.connectionFileHash
  }

  override def applyExtraSettings(settings: BspProjectSettings): Unit = {
    settings.buildOnSave = buildOnSaveCheckBox.isSelected
    settings.runPreImportTask = runPreImportTaskCheckBox.isSelected
    settings.autoRegenerateBspConfigOnServerStartup = autoRegenerateBspConfigCheckBox.isSelected
    // It's required, even for settings that don't have corresponding checkboxes,
    // to preserve the existing values in the project settings instead of using defaults.
    settings.preImportConfig = preImportConfig
    settings.serverConfig = serverConfig
    settings.bspConfigGenerated = bspConfigGenerated
    settings.connectionFileHash = connectionFileHash
  }

  override def validate(settings: BspProjectSettings): Boolean = true

  override def updateInitialExtraSettings(): Unit = {
    applyExtraSettings(getInitialSettings)
  }

}


/** A dummy to satisfy interface constraints of ExternalSystem */
trait BspProjectSettingsListener extends ExternalSystemSettingsListener[BspProjectSettings]

class BspProjectSettingsListenerAdapter(listener: ExternalSystemSettingsListener[BspProjectSettings])
  extends DelegatingExternalSystemSettingsListener[BspProjectSettings](listener) with BspProjectSettingsListener {
  override def onProjectRenamed(oldName: String, newName: String): Unit = {}
  override def onProjectsLoaded(settings: util.Collection[BspProjectSettings]): Unit = {}
  override def onProjectsLinked(settings: util.Collection[BspProjectSettings]): Unit = {}
  override def onProjectsUnlinked(linkedProjectPaths: util.Set[String]): Unit = {}
  override def onBulkChangeStart(): Unit = {}
  override def onBulkChangeEnd(): Unit = {}
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
   
  override def subscribe(listener: ExternalSystemSettingsListener[BspProjectSettings], parentDisposable: Disposable):Unit =
    doSubscribe(new BspProjectSettingsListenerAdapter(listener), parentDisposable)

  override def copyExtraSettingsFrom(settings: BspSettings): Unit = {}

  override def checkSettings(old: BspProjectSettings, current: BspProjectSettings): Unit = {}

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
  reportStatistic = true,
  category = SettingsCategory.TOOLS
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

class BspExecutionSettings(val basePath: Path,
                           val traceBsp: Boolean,
                           val runPreImportTask: Boolean,
                           val preImportTask: PreImportConfig,
                           val config: BspServerConfig
                          ) extends ExternalSystemExecutionSettings

object BspExecutionSettings {

  def executionSettingsFor(project: Project, basePath: Path): BspExecutionSettings = {
    if (project == null) executionSettingsFor(basePath)
    val bspSettings = BspSettings.getInstance(project)
    val bspTraceLog = BspSystemSettings.getInstance.getState.traceBsp
    val linkedSettings = Option(bspSettings.getLinkedProjectSettings(basePath.toCanonicalPath.toString))
    val runPreImportTask = linkedSettings.forall(_.runPreImportTask)
    val preImportConfig = linkedSettings.map(_.preImportConfig).getOrElse(AutoPreImport)
    val serverConfig = linkedSettings.map(_.serverConfig).getOrElse(AutoConfig)

    new BspExecutionSettings(basePath, bspTraceLog, runPreImportTask, preImportConfig, serverConfig)
  }

  def executionSettingsFor(basePath: Path): BspExecutionSettings = {
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
