package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.openapi.project.{DumbService, Project}
import org.jetbrains.plugins.scala.compiler.highlighting.services.HighlightingService

final class DumbModeListener(project: Project) extends DumbService.DumbModeListener:
  override def exitDumbMode(): Unit = HighlightingService(project).request("Exited dumb mode")
