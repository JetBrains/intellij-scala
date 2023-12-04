package org.jetbrains.plugins.scala.bsp.config

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import org.jetbrains.plugins.bsp.extension.points.BuildToolId

object ScalaPluginConstants {
  private val ID = "scalabsp"
  val BUILD_TOOL_ID: BuildToolId = new BuildToolId("sbt")
  val SYSTEM_ID = new ProjectSystemId(ID, "Scala BSP")
  private val BUILD_FILE_NAMES = List("build.sbt")
  val SUPPORTED_EXTENSIONS: List[String] = List("scala", "sc")
  val SUPPORTED_CONFIG_FILE_NAMES: List[String] = BUILD_FILE_NAMES
}
