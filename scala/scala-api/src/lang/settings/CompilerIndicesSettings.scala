package org.jetbrains.plugins.scala.settings

import com.intellij.openapi.components.{PersistentStateComponent, State, Storage}
import com.intellij.openapi.project.Project
import com.intellij.task.impl.{JpsProjectTaskRunner, ProjectTaskList}
import com.intellij.task.{ProjectTaskManager, ProjectTaskRunner}
import org.jetbrains.sbt.shell.SbtProjectTaskRunner

import scala.beans.BooleanBeanProperty
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

@State(
  name     = "CompilerIndicesSettings",
  storages = Array(new Storage("compiler_indices_settings.xml")),
  reportStatistic = true
)
class CompilerIndicesSettings(project: Project) extends PersistentStateComponent[CompilerIndicesSettings.State] {
  private[this] var state: CompilerIndicesSettings.State = new CompilerIndicesSettings.State

  private[this] val taskManager = ProjectTaskManager.getInstance(project)
  private[this] val runners     = ProjectTaskRunner.EP_NAME.getExtensionList.asScala

  /** Corresponds to the actual value set in configurable, consider using
    * [[isBytecodeIndexingActive]] to check if the indexing is explicitly enabled AND
    * makes sense in the context of the current project (i.e. this is a project built
    * with sbt shell or IDEA's JPS builder).
    */
  def isIndexingEnabled: Boolean                   = state.isIndexingEnabled()
  def isEnabledForImplicitDefs: Boolean            = state.isEnabledForImplicitDefs()
  def isEnabledForApplyUnapply: Boolean            = state.isEnabledForApplyUnapply()
  def isEnabledForSAMTypes: Boolean                = state.isEnabledForSAMTypes()
  def isEnabledForForComprehensionMethods: Boolean = state.isEnabledForForCompMethods()

  def setEnabledForImplicitDefs(enabled:            Boolean): Unit = state.setEnabledForImplicitDefs(enabled)
  def setEnabledForApplyUnapply(enabled:            Boolean): Unit = state.setEnabledForApplyUnapply(enabled)
  def setEnabledForSAMTypes(enabled:                Boolean): Unit = state.setEnabledForSAMTypes(enabled)
  def setEnabledForForComprehensionMethods(enabled: Boolean): Unit = state.setEnabledForForCompMethods(enabled)

  def setIndexingEnabled(v: Boolean): Unit = {
    state.indexingEnabled = v
  }

  //noinspection ApiStatus
  private[this] def hasCompatibleRunner: Boolean =
    runners.find { runner =>
      val task = taskManager.createAllModulesBuildTask(true, project)
      val moduleBuildTasks = task match {
        case taskList: ProjectTaskList => taskList.asScala
        case t                         => List(t)
      }

      try moduleBuildTasks.forall(runner.canRun(project, _))
      catch { case NonFatal(_) => false }
    }.exists {
      case _: JpsProjectTaskRunner => true
      case _: SbtProjectTaskRunner => true
      case _ => false
    }

  def isBytecodeIndexingActive: Boolean = isIndexingEnabled && hasCompatibleRunner

  override def getState: CompilerIndicesSettings.State                = state
  override def loadState(loaded: CompilerIndicesSettings.State): Unit = state = loaded
}

object CompilerIndicesSettings {
  class State {
    @BooleanBeanProperty var indexingEnabled: Boolean          = true
    @BooleanBeanProperty var enabledForImplicitDefs: Boolean   = true
    @BooleanBeanProperty var enabledForApplyUnapply: Boolean   = true
    @BooleanBeanProperty var enabledForSAMTypes: Boolean       = true
    @BooleanBeanProperty var enabledForForCompMethods: Boolean = true
  }

  def apply(project: Project): CompilerIndicesSettings =
    project.getService(classOf[CompilerIndicesSettings])
}
