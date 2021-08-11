package org.jetbrains.plugins.scala.worksheet.settings.persistent

import com.intellij.openapi.components._
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
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
final class WorksheetProjectDefaultPersistentSettings
  extends PersistentStateComponent[WorksheetProjectDefaultPersistentSettings.State]
    with WorksheetPersistentSettings {

  private val state = new WorksheetProjectDefaultPersistentSettings.State
  override def getState: WorksheetProjectDefaultPersistentSettings.State = state
  override def loadState(state: WorksheetProjectDefaultPersistentSettings.State): Unit = XmlSerializerUtil.copyBean(state, this.state)

  def getRunType: WorksheetExternalRunType = getState.getRunType()
  def isInteractive: Boolean = getState.isInteractive()
  def isMakeBeforeRun: Boolean = getState.isMakeBeforeRun()
  def getModuleName: Option[String] = Option(getState.moduleName)
  def getCompilerProfileName: Option[String] = Option(getState.compilerProfileName)

  override def setRunType(value: WorksheetExternalRunType): Unit = getState.setRunType(value)
  override def setInteractive(value: Boolean): Unit = getState.setInteractive(value)
  override def setMakeBeforeRun(value: Boolean): Unit = getState.setMakeBeforeRun(value)
  override def setModuleName(name: String): Unit = getState.setModuleName(name)
  override def setCompilerProfileName(name: String): Unit = getState.setCompilerProfileName(name)
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
    @BeanProperty var compilerProfileName: String = ScalaCompilerConfiguration.DefaultProfileName
  }
}