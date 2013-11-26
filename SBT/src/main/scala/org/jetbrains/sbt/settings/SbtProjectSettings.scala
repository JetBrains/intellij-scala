package org.jetbrains.sbt
package settings

import com.intellij.openapi.components._
import scala.Array
import scala.beans.BeanProperty
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.openapi.project.Project

/**
 * User: Dmitry Naydanov
 * Date: 11/22/13
 */
@State (
  name = "SbtNonExternalProjectSettings",
  storages = Array(
    new Storage(file = StoragePathMacros.PROJECT_FILE),
    new Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/sbt.xml", scheme = StorageScheme.DIRECTORY_BASED)
  )
)
class SbtProjectSettings extends PersistentStateComponent[SbtProjectSettings] {
  @BeanProperty
  var version: String = "0.13.0"
  
  def loadState(state: SbtProjectSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  def getState: SbtProjectSettings = this
}

object SbtProjectSettings {
  def instance(project: Project) = ServiceManager.getService(project, classOf[SbtProjectSettings])
  def getSbtVersion(project: Project) = instance(project).getVersion
}
