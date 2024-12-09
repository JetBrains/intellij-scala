package org.jetbrains.plugins.scala.bsp.flow.open

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.bsp.impl.flow.open.BaseBspProjectOpenProcessor
import org.jetbrains.plugins.scala.bsp.MillBspBundle
import org.jetbrains.plugins.scala.bsp.config.MillBspPluginConstants.{BSP_CONNECTION_DIR, BUILD_TOOL_ID, MILL_CONFIG_FILE}

import scala.annotation.tailrec

class MillBspProjectOpenProcessor extends BaseBspProjectOpenProcessor(BUILD_TOOL_ID) {
  override def getName: String = MillBspBundle.message("scala.mill.bsp.get.name")

  override def canOpenProject(projectPath: VirtualFile): Boolean =
    Registry.`is`("scala.bsp.plugin.mill.support") && projectPath != null && projectPath.findChild(MILL_CONFIG_FILE) != null

  override def calculateProjectFolderToOpen(virtualFile: VirtualFile): VirtualFile = {
    if (isMillConnectionFile(virtualFile)) {
      val parent = virtualFile.getParent
      if (parent.getName != BSP_CONNECTION_DIR)
        throw new IllegalArgumentException(MillBspBundle.message("mill.connection.file.wrong.directory"))
      parent.getParent
    } else {
      findProjectFolder(virtualFile) match {
        case Some(projectFolder) => projectFolder
        case None => throw new IllegalArgumentException(MillBspBundle.message("mill.no.project.found", virtualFile))
      }
    }
  }

  private def isMillConnectionFile(virtualFile: VirtualFile): Boolean =
    virtualFile.getName == MILL_CONFIG_FILE

  @tailrec
  private def findProjectFolder(virtualFile: VirtualFile): Option[VirtualFile] = {
    if (virtualFile == null)
      None
    else if (virtualFile.getChildren.exists(_.getName == MILL_CONFIG_FILE))
      Some(virtualFile)
    else
      findProjectFolder(virtualFile.getParent)
  }
}

