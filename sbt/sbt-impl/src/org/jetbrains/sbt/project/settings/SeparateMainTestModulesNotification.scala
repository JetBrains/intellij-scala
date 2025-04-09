package org.jetbrains.sbt.project.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.{PersistentStateComponent, Service, State, Storage}
import com.intellij.util.xmlb.XmlSerializerUtil

import scala.beans.BeanProperty

@Service(Array(Service.Level.APP))
@State(
  name = "SeparateMainTestModulesNotification",
  storages = Array(new Storage("sbt_separate_main_test_modules.xml"))
)
final class SeparateMainTestModulesNotification extends PersistentStateComponent[SeparateMainTestModulesNotification]{

  @BeanProperty
  var isShown: Boolean = false

  override def getState: SeparateMainTestModulesNotification = this

  override def loadState(state: SeparateMainTestModulesNotification): Unit =
    XmlSerializerUtil.copyBean(state, this)
}

object SeparateMainTestModulesNotification {

  def isShown: Boolean = getInstance.isShown

  def setShown(): Unit =
    getInstance.isShown = true

  private def getInstance: SeparateMainTestModulesNotification =
    ApplicationManager.getApplication().getService(classOf[SeparateMainTestModulesNotification])
}
