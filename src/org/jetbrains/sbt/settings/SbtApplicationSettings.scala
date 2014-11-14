package org.jetbrains.sbt
package settings

import com.intellij.openapi.components.{PersistentStateComponent, _}
import com.intellij.util.xmlb.XmlSerializerUtil

import scala.beans.BeanProperty

/**
 * @author Pavel Fatin
 */
@State(name = "ScalaSbtSettings", storages = Array(new Storage(file = StoragePathMacros.APP_CONFIG + "/scala_sbt.xml")))
class SbtApplicationSettings extends PersistentStateComponent[SbtApplicationSettings] {
  @BeanProperty
  var customLauncherEnabled: Boolean = false

  @BeanProperty
  var customLauncherPath: String = ""

  @BeanProperty
  var maximumHeapSize: String = "768"

  @BeanProperty
  var vmParameters: String = "-XX:MaxPermSize=384M"

  @BeanProperty
  var customVMEnabled: Boolean = false

  @BeanProperty
  var customVMPath: String = ""

  @BeanProperty
  var customSbtStructureDir: String = ""

  def getState = this

  def loadState(state: SbtApplicationSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }
}

object SbtApplicationSettings {
  def instance = ServiceManager.getService(classOf[SbtApplicationSettings])
}
