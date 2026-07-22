package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ModuleRootEvent, ModuleRootListener}
import org.jetbrains.plugins.scala.compiler.highlighting.services.{CompilationLogicService, SaveService}
import org.jetbrains.plugins.scala.compiler.highlighting.triggers.EditorTrigger
import org.jetbrains.plugins.scala.compiler.tracing.Tracing

private final class CompilerHighlightingModuleRootListener(project: Project) extends ModuleRootListener {
  
  private val tracer = Tracing(project)
  private val logic = CompilationLogicService(project)
  private val saveService = SaveService(project)
  
  override def rootsChanged(event: ModuleRootEvent): Unit = {
    if (project.isDisposed) return

    // Unconditionally, unlike the compilation below: the gate exists to avoid compiling several times for one
    // sync, and skipping an invalidation is the failure that does not correct itself.
    logic.invalidateAll()

    if (event.isCausedByWorkspaceModelChangesOnly) {
      // The rootsChanged event is fired multiple times after project sync. Checking for
      // `isCausedByWorkspaceModelChangesOnly` makes sure that we do not trigger multiple compilations.
      // Ensure that the project will be saved before the next compilation.
      saveService.enableProjectSave()
      EditorTrigger.triggerOnSelectedEditor(project, "module roots changed (workspace model)")
    }
  }
}
