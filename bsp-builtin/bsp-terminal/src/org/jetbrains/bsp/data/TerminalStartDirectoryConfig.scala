package org.jetbrains.bsp.data

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider

import scala.jdk.CollectionConverters._

// If the IJ project root does not match workspace git root, the terminal
// starting directory will be set to IJ project root, which is usually not
// what users want and they need to change the directory each time.
// This class convigures terminal start dir to the vcs root.
class TerminalStartDirectoryConfig extends BspVcsRootExtension {

  def onVcsRootAdded(project: Project): Unit = {
    val terminal = project.getService(classOf[TerminalProjectOptionsProvider])
    if (terminal.getDefaultStartingDirectory == terminal.getStartingDirectory) {
      val vcsManager = ProjectLevelVcsManager.getInstance(project)
      val currentMappings = vcsManager.getDirectoryMappings
      currentMappings.asScala.map(_.getDirectory).sortBy(-_.length).headOption.foreach { longestVCSRootPath =>
        terminal.setStartingDirectory(longestVCSRootPath)
      }
    }
  }

}
