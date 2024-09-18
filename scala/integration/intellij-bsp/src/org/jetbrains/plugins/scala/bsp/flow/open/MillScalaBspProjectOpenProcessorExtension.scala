package org.jetbrains.plugins.scala.bsp.flow.open

import org.jetbrains.plugins.bsp.config.BuildToolId
import org.jetbrains.plugins.bsp.impl.flow.open.BspProjectOpenProcessorExtension
import org.jetbrains.plugins.scala.bsp.config.MillScalaPluginConstants

class MillScalaBspProjectOpenProcessorExtension extends BspProjectOpenProcessorExtension {
  override def getShouldBspProjectOpenProcessorBeAvailable: Boolean = false

  override def getBuildToolId: BuildToolId = MillScalaPluginConstants.BUILD_TOOL_ID
}