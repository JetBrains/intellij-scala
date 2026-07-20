package org.jetbrains.plugins.scala.compiler.tracing

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.tracing.core.*
import org.jetbrains.plugins.scala.compiler.tracing.core.events.ContextTraceEvent

/**
 * Entry point for tracing.
 *
 * Tracing is project-scoped, so this object just resolves that service for a `project` and hands back
 * its [[TracingOps]]. */
object Tracing:
  private object NoOpTracing:
    lazy val ops = new NoOpTracingOps[ContextTraceEvent]

  def apply(project: Project): TracingOps[ContextTraceEvent] =
    if TracingConfig.isTracingEnabled
      then tracingFor(project).ops
      else NoOpTracing.ops


  private inline def tracingFor(project: Project) = project.getService(classOf[TracingService])
