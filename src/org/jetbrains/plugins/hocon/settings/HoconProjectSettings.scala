package org.jetbrains.plugins.hocon.settings

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components._
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

import scala.beans.BeanProperty

@State(
  name = "HoconProjectSettings",
  storages = Array(
    new Storage(StoragePathMacros.WORKSPACE_FILE),
    new Storage("hocon_settings.xml")
  )
)
class HoconProjectSettings extends PersistentStateComponent[HoconProjectSettings] with ExportableComponent {
  def getState: HoconProjectSettings = this

  def loadState(state: HoconProjectSettings): Unit =
    XmlSerializerUtil.copyBean(state, this)

  def getPresentableName = "HOCON Project Settings"

  def getExportFiles =
    Array(PathManager.getOptionsFile("hocon_project_settings"))

  @BeanProperty var classReferencesOnUnquotedStrings = true
  @BeanProperty var classReferencesOnQuotedStrings = true
}

object HoconProjectSettings {
  def getInstance(project: Project): HoconProjectSettings =
    ServiceManager.getService(project, classOf[HoconProjectSettings])
}
