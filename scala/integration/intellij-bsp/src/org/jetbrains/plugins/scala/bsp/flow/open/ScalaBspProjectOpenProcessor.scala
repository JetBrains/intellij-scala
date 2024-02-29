package org.jetbrains.plugins.scala.bsp.flow.open

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.bsp.flow.open.BaseBspProjectOpenProcessor
import org.jetbrains.plugins.scala.bsp.config.ScalaPluginConstants


class ScalaBspProjectOpenProcessor extends BaseBspProjectOpenProcessor(ScalaPluginConstants.BUILD_TOOL_ID) {
  override def getName: String = "Sbt over BSP"

  override def canOpenProject(projectPath: VirtualFile): Boolean =
    projectPath != null && projectPath.findChild(ScalaPluginConstants.SBT_CONFIG_FILE) != null
}
