package org.jetbrains.bsp.project.test.environment

import com.intellij.execution.BeforeRunTask
import org.jdom.Element
import com.intellij.openapi.util.Key

object BspFetchTestEnvironmentTask {
  val runTaskKey: Key[BspFetchTestEnvironmentTask] = Key.create("BSP.BeforeRunTask")

  val jvmTestEnvironmentKey: Key[JvmTestEnvironment] = Key.create("BSP.JvmTestEnvironment")
}
class BspFetchTestEnvironmentTask extends BeforeRunTask[BspFetchTestEnvironmentTask](BspFetchTestEnvironmentTask.runTaskKey) {
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
