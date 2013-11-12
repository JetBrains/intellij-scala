package org.jetbrains.sbt
package settings

import com.intellij.openapi.components._
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.util.xmlb.XmlSerializerUtil
import beans.BeanProperty

/**
 * @author Pavel Fatin
 */
@State(name = "SbtSettings", storages = Array(new Storage(file = StoragePathMacros.APP_CONFIG + "/sbt.xml")))
class SbtApplicationSettings extends PersistentStateComponent[SbtApplicationSettings] {
  @BeanProperty
  var customLauncherEnabled: Boolean = false

  @BeanProperty
  var customLauncherPath: String = ""

  @BeanProperty
  var maximumHeapSize: String = "768"

  @BeanProperty
  var vmParameters: String = "-XX:MaxPermSize=384M"

  def getState = this

  def loadState(state: SbtApplicationSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }
}

object SbtApplicationSettings {
  def instance = ServiceManager.getService(classOf[SbtApplicationSettings])
}
