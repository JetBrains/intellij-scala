package org.jetbrains.plugins.scala.bsp.flow.open

import org.jetbrains.plugins.bsp.extension.points.BuildToolId
import org.jetbrains.plugins.bsp.flow.open.BspProjectOpenProcessorExtension
import org.jetbrains.plugins.scala.bsp.config.ScalaPluginConstants

class ScalaBspProjectOpenProcessorExtension extends BspProjectOpenProcessorExtension {
  override def getShouldBspProjectOpenProcessorBeAvailable: Boolean = true

  override def getBuildToolId: BuildToolId = ScalaPluginConstants.BUILD_TOOL_ID
}
