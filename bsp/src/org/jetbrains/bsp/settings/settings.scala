package org.jetbrains.bsp.settings

import java.beans.BeanProperty
import java.io.File
import java.util

import com.intellij.openapi.components._
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings
import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.settings._
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil._
import com.intellij.openapi.externalSystem.util.{ExternalSystemSettingsControl, ExternalSystemUiUtil, PaintAwarePanel}
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.annotations.XCollection
import javax.swing.JCheckBox
import org.jetbrains.bsp.{BspBundle, _}

class BspProjectSettings extends ExternalProjectSettings {

  @BeanProperty
  var buildOnSave = false

  @BeanProperty
  var runPreImportTask = true

  override def clone(): BspProjectSettings = {
    val result = new BspProjectSettings
    copyTo(result)
    result.buildOnSave = buildOnSave
    result.runPreImportTask = runPreImportTask
    result
  }
}

class BspProjectSettingsControl(settings: BspProjectSettings)
  extends AbstractExternalProjectSettingsControl[BspProjectSettings](null, settings) {

  @BeanProperty
  var buildOnSave = false

  @BeanProperty
  var runPreImportTask = true

  private val buildOnSaveCheckBox = new JCheckBox(BspBundle.message("build.automatically.on.file.save"))
  private val runPreImportTaskCheckBox = new JCheckBox(BspBundle.message("export.sbt.projects.to.bloop.before.import"))

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
  def onBuildOnSaveChanged(buildOnSave: Boolean)
  def onRunPreImportTaskChanged(runBloopInstall: Boolean)
}

class BspProjectSettingsListenerAdapter(listener: ExternalSystemSettingsListener[BspProjectSettings])
  extends DelegatingExternalSystemSettingsListener[BspProjectSettings](listener) with BspProjectSettingsListener {
  override def onBuildOnSaveChanged(buildOnSave: Boolean): Unit = {}
  override def onRunPreImportTaskChanged(runBloopInstall: Boolean): Unit = {}
}


object BspTopic extends Topic[BspProjectSettingsListener](BspBundle.message("bsp.specific.settings"), classOf[BspProjectSettingsListener])

@State(
  name = "BspSettings",
  storages = Array(new Storage("bsp.xml"))
)
class BspSettings(project: Project)
  extends AbstractExternalSystemSettings[BspSettings, BspProjectSettings, BspProjectSettingsListener](BspTopic, project)
    with PersistentStateComponent[BspSettings.State]
{

  def getSystemSettings: BspSystemSettings = BspSystemSettings.getInstance

  override def subscribe(listener: ExternalSystemSettingsListener[BspProjectSettings]): Unit = {
    val adapter = new BspProjectSettingsListenerAdapter(listener)
    getProject.getMessageBus.connect(getProject).subscribe(BspTopic, adapter)
  }

  override def copyExtraSettingsFrom(settings: BspSettings): Unit = {}

  override def checkSettings(old: BspProjectSettings, current: BspProjectSettings): Unit = {
    if(old.buildOnSave != current.buildOnSave)
      getPublisher.onBuildOnSaveChanged(current.buildOnSave)
    if(old.runPreImportTask != current.runPreImportTask)
      getPublisher.onRunPreImportTaskChanged(current.runPreImportTask)
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
  def getInstance: BspSystemSettings = ServiceManager.getService(classOf[BspSystemSettings])

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
                           val runPreImportTask: Boolean
                          ) extends ExternalSystemExecutionSettings

object BspExecutionSettings {

  def executionSettingsFor(project: Project, basePath: File): BspExecutionSettings = {
    if (project == null) executionSettingsFor(basePath)
    val bspSettings = BspSettings.getInstance(project)
    val bspTraceLog = BspSystemSettings.getInstance.getState.traceBsp
    val runPreImportTask = Option(bspSettings.getLinkedProjectSettings(basePath.getAbsolutePath)).forall(_.runPreImportTask)

    new BspExecutionSettings(basePath, bspTraceLog, runPreImportTask)
  }

  def executionSettingsFor(basePath: File): BspExecutionSettings = {
    val systemSettings = BspSystemSettings.getInstance
    val defaultProjectSettings = new BspProjectSettings
    new BspExecutionSettings(basePath, systemSettings.getState.traceBsp, defaultProjectSettings.runPreImportTask)
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
