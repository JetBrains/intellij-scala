package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.importing.AbstractOpenProjectProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

@Suppress("UnstableApiUsage")
abstract class AbstractBuildToolOpenProjectProvider : AbstractOpenProjectProvider() {

  override suspend fun linkProject(projectFile: VirtualFile, project: Project) {
    val projectDirectory = getProjectDirectory(projectFile)
    doLinkProject(projectDirectory, project)
  }

  protected abstract fun doLinkProject(projectDirectory: VirtualFile, project: Project)
}
