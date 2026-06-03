package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.ide.trustedProjects.TrustedProjects
import org.jetbrains.sbt.SbtUtil

private class SbtProjectDataImportListener(project: Project) extends ProjectDataImportListener {
  /**
   * Determines if the listener is allowed to proceed with the external project in the given path.
   * This includes checks to ensure the project is an sbt project,
   * the project is trusted, and it is not in preview mode.
   */
  protected def isListenerAllowed(projectPath: String): Boolean = {
    val isTrustedProject = TrustedProjects.isProjectTrusted(project)
    val isPreview = SbtUtil.isPreview(project, projectPath)
    SbtUtil.isSbtProject(project) && isTrustedProject && !isPreview
  }
}
