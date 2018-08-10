package org.jetbrains.bsp.settings

import java.io.File
import java.util

import com.intellij.openapi.components._
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings
import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.settings._
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil._
import com.intellij.openapi.externalSystem.util.{ExternalSystemSettingsControl, ExternalSystemUiUtil, PaintAwarePanel}
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtilRt
import com.intellij.util.messages.Topic
import javax.swing.JCheckBox
import org.jetbrains.bsp._

import scala.beans.BeanProperty

class BspProjectSettings extends ExternalProjectSettings {

  @BeanProperty
  var buildOnSave = false

  override def clone(): BspProjectSettings = {
    val result = new BspProjectSettings
    copyTo(result)
    result.buildOnSave = buildOnSave
    result
  }
}

// TODO the hell is up with this setting duplication
class BspProjectSettingsControl(settings: BspProjectSettings)
  extends AbstractExternalProjectSettingsControl[BspProjectSettings](null, settings, null) {

  @BeanProperty
  var buildOnSave = false

  private val buildOnSaveCheckBox = new JCheckBox("build automatically on file save")

  override def fillExtraControls(content: PaintAwarePanel, indentLevel: Int): Unit = {
    val fillLineConstraints = getFillLineConstraints(1)
    content.add(buildOnSaveCheckBox, fillLineConstraints)
  }

  override def isExtraSettingModified: Boolean = {
    val initial = getInitialSettings
    buildOnSaveCheckBox.isSelected != initial.buildOnSave
  }

  override def resetExtraSettings(isDefaultModuleCreation: Boolean): Unit = {
    val initial = getInitialSettings
    buildOnSaveCheckBox.setSelected(initial.buildOnSave)
  }

  override def applyExtraSettings(settings: BspProjectSettings): Unit = {
    settings.buildOnSave = buildOnSaveCheckBox.isSelected
  }

  override def validate(settings: BspProjectSettings): Boolean = true

  override def updateInitialExtraSettings(): Unit = {
    applyExtraSettings(getInitialSettings)
  }

}


/** A dummy to satisfy interface constraints of ExternalSystem */
trait BspProjectSettingsListener extends ExternalSystemSettingsListener[BspProjectSettings]

class BspProjectSettingsListenerAdapter(listener: ExternalSystemSettingsListener[BspProjectSettings])
  extends DelegatingExternalSystemSettingsListener[BspProjectSettings](listener) with BspProjectSettingsListener


object BspTopic extends Topic[BspProjectSettingsListener]("bsp-specific settings", classOf[BspProjectSettingsListener])

@State(
  name = "BspSettings",
  storages = Array(new Storage("bsp.xml"))
)
class BspSystemSettings(project: Project)
  extends AbstractExternalSystemSettings[BspSystemSettings, BspProjectSettings, BspProjectSettingsListener](BspTopic, project)
    with PersistentStateComponent[BspSystemSettingsState]
{

  @BeanProperty
  var myState: BspSystemSettingsState = new BspSystemSettingsState

  override def subscribe(listener: ExternalSystemSettingsListener[BspProjectSettings]): Unit = {
    val adapter = new BspProjectSettingsListenerAdapter(listener)
    getProject.getMessageBus.connect(getProject).subscribe(BspTopic, adapter)
  }

  override def copyExtraSettingsFrom(settings: BspSystemSettings): Unit = {}

  override def checkSettings(old: BspProjectSettings, current: BspProjectSettings): Unit = {}

  override def getState: BspSystemSettingsState = {
    fillState(myState)
    myState
  }

  override def loadState(state: BspSystemSettingsState): Unit = {
    super[AbstractExternalSystemSettings].loadState(state)
    myState = state
  }
}

object BspSystemSettings {
  def getInstance(project: Project): BspSystemSettings = ServiceManager.getService(project, classOf[BspSystemSettings])
}

class BspSystemSettingsState extends AbstractExternalSystemSettings.State[BspProjectSettings] {

  @BeanProperty
  var bloopPath: String = "bloop"

  private val projectSettings = ContainerUtilRt.newTreeSet[BspProjectSettings]()

  override def getLinkedExternalProjectsSettings: util.Set[BspProjectSettings] = projectSettings
  override def setLinkedExternalProjectsSettings(settings: util.Set[BspProjectSettings]): Unit =
    projectSettings.addAll(settings)
}

@State(
  name = "BspLocalSettings",
  storages = Array(new Storage(StoragePathMacros.WORKSPACE_FILE))
)
class BspLocalSettings(project: Project)
  extends AbstractExternalSystemLocalSettings[BspLocalSettingsState](bsp.ProjectSystemId, project)
    with PersistentStateComponent[BspLocalSettingsState] {

  override def loadState(state: BspLocalSettingsState): Unit =
    super[AbstractExternalSystemLocalSettings].loadState(state)
}

object BspLocalSettings {
  def getInstance(project: Project): BspLocalSettings = ServiceManager.getService(project, classOf[BspLocalSettings])
}

class BspLocalSettingsState extends AbstractExternalSystemLocalSettings.State

class BspExecutionSettings(val basePath: File, val bloopExecutable: File) extends ExternalSystemExecutionSettings

object BspExecutionSettings {

  def executionSettingsFor(project: Project, path: String): BspExecutionSettings = {
    val systemSettings = BspSystemSettings.getInstance(project)

    val basePath = new File(path)
    val bloopExecutable = new File(systemSettings.getState.bloopPath)
    new BspExecutionSettings(basePath, bloopExecutable)
  }
}

class BspSystemSettingsControl(settings: BspSystemSettings) extends ExternalSystemSettingsControl[BspSystemSettings] {

  private val pane = new BspSystemSettingsPane

  override def fillUi(canvas: PaintAwarePanel, indentLevel: Int): Unit = {
    canvas.add(pane.content, ExternalSystemUiUtil.getFillLineConstraints(indentLevel))
  }

  override def showUi(show: Boolean): Unit ={
    pane.content.setVisible(show)
  }

  override def reset(): Unit = {
    pane.bloopExecutablePath.setText(settings.getState.bloopPath)
    pane.setPathListeners()
  }

  override def isModified: Boolean =
    pane.bloopExecutablePath.getText != settings.getState.bloopPath

  override def apply(settings: BspSystemSettings): Unit = {
    settings.getState.bloopPath = pane.bloopExecutablePath.getText
  }
  override def validate(settings: BspSystemSettings): Boolean =
    true // TODO validate bloop path or something?

  override def disposeUIResources(): Unit = {}
}
