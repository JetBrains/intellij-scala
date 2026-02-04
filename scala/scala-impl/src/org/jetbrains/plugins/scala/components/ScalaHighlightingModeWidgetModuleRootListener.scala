package org.jetbrains.plugins.scala.components

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ModuleRootEvent, ModuleRootListener}

private final class ScalaHighlightingModeWidgetModuleRootListener(project: Project) extends ModuleRootListener {
  override def rootsChanged(event: ModuleRootEvent): Unit = {
    if (!event.isCausedByWorkspaceModelChangesOnly) return
    ScalaHighlightingModeWidgetUpdater.scheduleWidgetUpdate(project)
  }
}
