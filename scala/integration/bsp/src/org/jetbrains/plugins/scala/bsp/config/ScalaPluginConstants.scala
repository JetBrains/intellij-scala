package org.jetbrains.plugins.scala.bsp.config

import com.intellij.openapi.externalSystem.model.ProjectSystemId
object ScalaPluginConstants {
  private val ID = "scalabsp"
  val SYSTEM_ID = new ProjectSystemId(ID, "Scala BSP")
  private val WORKSPACE_FILE_NAMES = List()
  private val BUILD_FILE_NAMES = List("build.sbt")
  val SUPPORTED_EXTENSIONS: List[String] = List("scala", "sc")
  val SUPPORTED_CONFIG_FILE_NAMES: List[String] = WORKSPACE_FILE_NAMES ++ BUILD_FILE_NAMES


}
