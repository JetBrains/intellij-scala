package org.jetbrains.bsp.project

import com.intellij.openapi.project.Project
import org.jetbrains.bsp.settings.BspSettings
import org.jetbrains.sbt.project.SbtUnlinkedProjectAwareHelper

class BspSbtUnlinkedProjectAwareHelper extends SbtUnlinkedProjectAwareHelper {

  override def isLinkedProject(project: Project, externalProjectPath: String): Boolean = {
    val settings = BspSettings.getInstance(project)
    settings.getLinkedProjectSettings(externalProjectPath) != null
  }
}
