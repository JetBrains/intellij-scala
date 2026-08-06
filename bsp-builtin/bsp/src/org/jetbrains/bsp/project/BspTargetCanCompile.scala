package org.jetbrains.bsp.project

import com.intellij.openapi.components.{PersistentStateComponent, Service, State, Storage, StoragePathMacros}
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

import java.util.Collections
import scala.beans.BeanProperty

@State(
  name = "BspTargetCanCompile",
  storages = Array(new Storage(StoragePathMacros.WORKSPACE_FILE))
)
@Service(Array(Service.Level.PROJECT))
final class BspTargetCanCompile
  extends PersistentStateComponent[BspTargetCanCompile] {

  @BeanProperty
  var compilableTargets: java.util.List[String] = Collections.emptyList()

  override def getState: BspTargetCanCompile = this

  override def loadState(state: BspTargetCanCompile): Unit = XmlSerializerUtil.copyBean(state, this)
}

object BspTargetCanCompile {
  def getInstance(project: Project): BspTargetCanCompile = {
    project.getService(classOf[BspTargetCanCompile])
  }
}