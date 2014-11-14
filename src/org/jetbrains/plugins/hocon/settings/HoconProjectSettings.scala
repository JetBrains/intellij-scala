package org.jetbrains.plugins.hocon.settings

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components._
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

import scala.beans.BeanProperty

@State(
  name = "HoconProjectSettings",
  storages = Array(
    new Storage(file = "$WORKSPACE_FILE$"),
    new Storage(file = "$PROJECT_CONFIG_DIR$/hocon_settings.xml", scheme = StorageScheme.DIRECTORY_BASED)
  )
)
class HoconProjectSettings extends PersistentStateComponent[HoconProjectSettings] with ExportableComponent {
  def getState = this

  def loadState(state: HoconProjectSettings) =
    XmlSerializerUtil.copyBean(state, this)

  def getPresentableName = "HOCON Project Settings"

  def getExportFiles =
    Array(PathManager.getOptionsFile("hocon_project_settings"))

  @BeanProperty var classReferencesOnUnquotedStrings = true
  @BeanProperty var classReferencesOnQuotedStrings = true
}

object HoconProjectSettings {
  def getInstance(project: Project) =
    ServiceManager.getService(project, classOf[HoconProjectSettings])
}
