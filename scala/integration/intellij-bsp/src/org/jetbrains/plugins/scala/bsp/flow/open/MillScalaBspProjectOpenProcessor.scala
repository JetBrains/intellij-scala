package org.jetbrains.plugins.scala.bsp.flow.open

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.bsp.impl.flow.open.BaseBspProjectOpenProcessor
import org.jetbrains.plugins.scala.bsp.MillBspBundle
import org.jetbrains.plugins.scala.bsp.config.MillScalaPluginConstants

class MillScalaBspProjectOpenProcessor extends BaseBspProjectOpenProcessor(MillScalaPluginConstants.BUILD_TOOL_ID) {
  override def getName: String = MillBspBundle.message("scala.mill.bsp.get.name")

  override def canOpenProject(projectPath: VirtualFile): Boolean =
    projectPath != null && projectPath.findChild(MillScalaPluginConstants.MILL_CONFIG_FILE) != null

  override def calculateProjectFolderToOpen(virtualFile: VirtualFile): VirtualFile = {
    Iterator.iterate(virtualFile.getParent)(_.getParent)
      .takeWhile(_ != null)
      .find(canOpenProject)
      .getOrElse(throw new IllegalStateException(MillBspBundle.message("mill.no.project.found", virtualFile)))
  }
}

