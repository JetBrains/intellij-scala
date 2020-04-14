package org.jetbrains.bsp.project.test.environment

import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import com.intellij.execution.BeforeRunTask
import com.intellij.openapi.util.Key
import org.jdom.Element
import org.jetbrains.bsp.project.test.environment.BspJvmEnvironment.BspTargetIdHolder

object BspFetchEnvironmentTask {
  val runTaskKey: Key[BspFetchEnvironmentTask] = Key.create("BSP.BeforeRunTask")

  val jvmEnvironmentKey: Key[JvmEnvironment] = Key.create("BSP.JvmTestEnvironment")
}

class BspFetchEnvironmentTask
  extends BeforeRunTask[BspFetchEnvironmentTask](BspFetchEnvironmentTask.runTaskKey)
  with BspTargetIdHolder {

  var selected: Option[BuildTargetIdentifier] = None

  val CHOSEN_TARGET = "CHOSEN_TARGET"


  override def currentValue: Option[BuildTargetIdentifier] = selected

  override def update(value: BuildTargetIdentifier): Unit = selected = Some(value)

  override def writeExternal(element: Element): Unit = {
    super.writeExternal(element)
    selected match {
      case Some(value) => element.setAttribute(CHOSEN_TARGET, value.getUri)
      case None => element.removeAttribute(CHOSEN_TARGET)
    }
  }

  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    selected = Option(element.getAttributeValue(CHOSEN_TARGET)).map(new BuildTargetIdentifier(_))
  }
}
