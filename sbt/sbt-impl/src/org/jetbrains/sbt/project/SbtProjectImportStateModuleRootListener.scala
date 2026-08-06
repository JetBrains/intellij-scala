package org.jetbrains.sbt.project

import com.intellij.openapi.roots.{ModuleRootEvent, ModuleRootListener}

private final class SbtProjectImportStateModuleRootListener extends ModuleRootListener {
  override def rootsChanged(event: ModuleRootEvent): Unit = {
    val project = event.getProject
    if (event.isCausedByWorkspaceModelChangesOnly && !project.isDisposed) {
      SbtProjectImportStateService.instance(project).reset()
    }
  }
}
