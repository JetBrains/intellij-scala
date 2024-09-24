package org.jetbrains.bsp.project.test.environment

import scala.beans.BeanProperty

import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.module.Module
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.bsp.project.test.environment.BspJvmEnvironment._

@State(
  name = "PersistentBspTargetIdHolder",
  storages = Array(new Storage(StoragePathMacros.MODULE_FILE))
)
class PersistentBspTargetIdHolder
  extends PersistentStateComponent[PersistentBspTargetIdHolder]
    with BspTargetIdHolder {

  @BeanProperty var selected: String = _

  override def loadState(t: PersistentBspTargetIdHolder): Unit = {
    XmlSerializerUtil.copyBean(t, this)
  }

  override def getState: PersistentBspTargetIdHolder = this

  override def currentValue: Option[BuildTargetIdentifier] = Option(selected).map(new BuildTargetIdentifier(_))

  override def update(value: BuildTargetIdentifier): Unit = {
    selected = value.getUri
  }
}

object PersistentBspTargetIdHolder {
  def getInstance(module: Module): BspTargetIdHolder = {
    module.getService(classOf[PersistentBspTargetIdHolder])
  }
}
