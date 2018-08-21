package org.jetbrains.sbt
package settings

import java.util

import com.intellij.openapi.components._
import com.intellij.openapi.externalSystem.settings.{AbstractExternalSystemSettings, ExternalSystemSettingsListener}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtilRt
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.NotNull
import org.jetbrains.sbt.project.settings.{SbtProjectSettings, SbtProjectSettingsListener, SbtProjectSettingsListenerAdapter, SbtTopic}
import org.jetbrains.sbt.settings.SbtSettings._

import scala.beans.BeanProperty

/**
 * @author Pavel Fatin
 */

@State(
  name = "ScalaSbtSettings",
  storages = Array(new Storage("sbt.xml"))
)
class SbtSettings(project: Project)
  extends AbstractExternalSystemSettings[SbtSettings, SbtProjectSettings, SbtProjectSettingsListener](SbtTopic, project)
  with PersistentStateComponent[SbtSystemSettingsState] {

  override def getState: SbtSystemSettingsState = {
    val state = new SbtSystemSettingsState
    fillState(state)
    state
  }
  override def loadState(state: SbtSystemSettingsState): Unit = {
    super[AbstractExternalSystemSettings].loadState(state)
  }

  override def subscribe(listener: ExternalSystemSettingsListener[SbtProjectSettings]) {
    val adapter = new SbtProjectSettingsListenerAdapter(listener)
    getProject.getMessageBus.connect(getProject).subscribe(SbtTopic, adapter)
  }

  override def copyExtraSettingsFrom(settings: SbtSettings) {}

  def getLinkedProjectSettings(module: Module): Option[SbtProjectSettings] =
    Option(ExternalSystemApiUtil.getExternalRootProjectPath(module)).safeMap(getLinkedProjectSettings)

  def getLinkedProjectSettings(element: PsiElement): Option[SbtProjectSettings] =
    for {
      virtualFile <- Option(element.getContainingFile).safeMap(_.getVirtualFile)
      projectFileIndex = ProjectRootManager.getInstance(element.getProject).getFileIndex
      module <- Option(projectFileIndex.getModuleForFile(virtualFile))
      if project == element.getProject
      projectSettings <- getLinkedProjectSettings(module)
    } yield projectSettings

  override def getLinkedProjectSettings(linkedProjectPath: String): SbtProjectSettings =
    Option(super.getLinkedProjectSettings(linkedProjectPath))
      .getOrElse(super.getLinkedProjectSettings(ExternalSystemApiUtil.normalizePath(linkedProjectPath)))

  override def checkSettings(old: SbtProjectSettings, current: SbtProjectSettings): Unit = {}
}

object SbtSettings {
  def getInstance(@NotNull project: Project): SbtSettings = ServiceManager.getService(project, classOf[SbtSettings])

  val defaultMaxHeapSize = "1536"
}

class SbtSystemSettingsState extends AbstractExternalSystemSettings.State[SbtProjectSettings] {

  private val linkedProjectSettings: util.TreeSet[SbtProjectSettings] = ContainerUtilRt.newTreeSet[SbtProjectSettings]()

  @BeanProperty
  var customLauncherEnabled: Boolean = false

  @BeanProperty
  var customLauncherPath: String = ""

  @BeanProperty
  var maximumHeapSize: String = defaultMaxHeapSize

  @BeanProperty
  var vmParameters: String = ""

  @BeanProperty
  var customVMEnabled: Boolean = false

  @BeanProperty
  var customVMPath: String = ""

  @BeanProperty
  var customSbtStructurePath: String = ""

  @XCollection(style = XCollection.Style.v1, elementTypes = Array(classOf[SbtProjectSettings]))
  override def getLinkedExternalProjectsSettings: util.Set[SbtProjectSettings] =
    linkedProjectSettings

  override def setLinkedExternalProjectsSettings(settings: util.Set[SbtProjectSettings]): Unit = {
    linkedProjectSettings.addAll(settings)
  }
}
