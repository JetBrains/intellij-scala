package org.jetbrains.plugins.scala.bsp.flow.open

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.bsp.impl.flow.open.BaseBspProjectOpenProcessor
import org.jetbrains.plugins.scala.bsp.config.MillScalaPluginConstants

class MillScalaBspProjectOpenProcessor extends BaseBspProjectOpenProcessor(MillScalaPluginConstants.BUILD_TOOL_ID) {
  override def getName: String = "BSP (experimental) with extended implementation"

  override def canOpenProject(projectPath: VirtualFile): Boolean =
    projectPath != null && projectPath.findChild(MillScalaPluginConstants.MILL_CONFIG_FILE) != null

  override def calculateProjectFolderToOpen(virtualFile: VirtualFile): VirtualFile = {
    Iterator.iterate(virtualFile.getParent)(_.getParent)
      .takeWhile(_ != null)
      .find(canOpenProject)
      .getOrElse(throw new IllegalStateException(s"Cannot find the suitable Mill project folder to open for the given file $virtualFile."))
  }
}

