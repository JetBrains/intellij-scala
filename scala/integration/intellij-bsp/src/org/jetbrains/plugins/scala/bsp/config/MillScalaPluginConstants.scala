package org.jetbrains.plugins.scala.bsp.config

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import org.jetbrains.plugins.bsp.config.BuildToolId

object MillScalaPluginConstants {
  val ID = "scalamillbsp"
  val BUILD_TOOL_ID: BuildToolId = new BuildToolId("millbsp")
  val SYSTEM_ID = new ProjectSystemId(ID, "Scala Mill BSP")
  val SUPPORTED_CONFIG_FILE_EXTENSIONS: List[String] = List("sc")
  val SUPPORTED_CONFIG_FILE_NAMES: List[String] = List("build")
  val MILL_CONFIG_FILE = "build.sc"
  val MILL_TAB_NAME = "mill"
}