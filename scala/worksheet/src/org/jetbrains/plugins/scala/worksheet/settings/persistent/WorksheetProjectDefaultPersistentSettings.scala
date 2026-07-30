package org.jetbrains.plugins.scala.worksheet.settings.persistent

import com.intellij.openapi.components._
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType

import scala.beans.{BeanProperty, BooleanBeanProperty}

@State(
  name = "WorksheetDefaultProjectSettings",
  storages = Array(
    new Storage(StoragePathMacros.WORKSPACE_FILE),
    new Storage("scala_settings.xml")
  ),
  reportStatistic = true
)
@Service(Array(Service.Level.PROJECT))
final class WorksheetProjectDefaultPersistentSettings
  extends PersistentStateComponent[WorksheetProjectDefaultPersistentSettings.State]
    with WorksheetPersistentSettings {

  private val state = new WorksheetProjectDefaultPersistentSettings.State
  override def getState: WorksheetProjectDefaultPersistentSettings.State = state
  override def loadState(state: WorksheetProjectDefaultPersistentSettings.State): Unit = XmlSerializerUtil.copyBean(state, this.state)

  def getRunType: WorksheetExternalRunType = getState.runType
  def isInteractive: Boolean = getState.interactive
  def isMakeBeforeRun: Boolean = getState.makeBeforeRun
  def getModuleName: Option[String] = Option(getState.moduleName)
  def getCompilerProfileName: Option[String] = Option(getState.compilerProfileName)

  override def setRunType(value: WorksheetExternalRunType): Unit = getState.runType = value
  override def setInteractive(value: Boolean): Unit = getState.interactive = value
  override def setMakeBeforeRun(value: Boolean): Unit = getState.makeBeforeRun = value
  override def setModuleName(name: String): Unit = getState.moduleName  = name
  override def setCompilerProfileName(name: String): Unit = getState.compilerProfileName = name
}

object WorksheetProjectDefaultPersistentSettings {

  def apply(project: Project): WorksheetProjectDefaultPersistentSettings =
    project.getService(classOf[WorksheetProjectDefaultPersistentSettings])

  //noinspection ConvertNullInitializerToUnderscore
  class State {
    @ReportValue
    @OptionTag(converter = classOf[WorksheetExternalRunTypeConverter])
    @BeanProperty
    var runType: WorksheetExternalRunType = WorksheetExternalRunType.getDefaultRunType

    @BooleanBeanProperty var interactive: Boolean = false
    @BooleanBeanProperty var makeBeforeRun: Boolean = false
    @BeanProperty var moduleName: String = null
    @BeanProperty var compilerProfileName: String = null
  }
}
