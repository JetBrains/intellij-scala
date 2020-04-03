package org.jetbrains.bsp.project.test.environment

import java.net.URI

import com.intellij.execution.BeforeRunTask
import org.jdom.Element
import com.intellij.openapi.util.Key

object BspFetchEnvironmentTask {
  val runTaskKey: Key[BspFetchEnvironmentTask] = Key.create("BSP.BeforeRunTask")

  val jvmEnvironmentKey: Key[JvmTestEnvironment] = Key.create("BSP.JvmTestEnvironment")
}
class BspFetchEnvironmentTask
  extends BeforeRunTask[BspFetchEnvironmentTask](BspFetchEnvironmentTask.runTaskKey) {

  var state: Option[String] = None

  val CHOSEN_TARGET = "CHOSEN_TARGET"

  override def writeExternal(element: Element): Unit = {
    super.writeExternal(element)
    state match {
      case Some(value) => element.setAttribute(CHOSEN_TARGET, value)
      case None => element.removeAttribute(CHOSEN_TARGET)
    }

  }

  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    state = Option(element.getAttributeValue(CHOSEN_TARGET))
  }
}
