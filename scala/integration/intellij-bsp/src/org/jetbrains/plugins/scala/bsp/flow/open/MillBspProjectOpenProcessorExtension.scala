package org.jetbrains.plugins.scala.bsp.flow.open

import org.jetbrains.plugins.bsp.config.BuildToolId
import org.jetbrains.plugins.bsp.impl.flow.open.BspProjectOpenProcessorExtension
import org.jetbrains.plugins.scala.bsp.config.MillBspPluginConstants

class MillBspProjectOpenProcessorExtension extends BspProjectOpenProcessorExtension {

  override def getShouldBspProjectOpenProcessorBeAvailable: Boolean = false

  override def getBuildToolId: BuildToolId = MillBspPluginConstants.BUILD_TOOL_ID
}