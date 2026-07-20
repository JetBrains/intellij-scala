package org.jetbrains.plugins.scala.compiler.tracing.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.compiler.tracing.core.events.ContextTraceEvent
import org.jetbrains.plugins.scala.compiler.tracing.core.otel.OtelConfig

import java.nio.file.Path

@Service(Array(Service.Level.PROJECT))
private[tracing] final class TracingService(project: Project) extends Disposable {

  private lazy val path: Path = TracingConfig.traceFilePath(project)

  private lazy val registry: Registry[Any, TraceSpan[ContextTraceEvent]] = new QueueRegistry
  // keep lazy otherwise otel will start writing to file
  private lazy val service = OtelConfig.service(path)

  private[tracing] lazy val ops: TracingOps[ContextTraceEvent] = new ContextTracingOps(service, registry)

  /**
   * Writes out the spans still buffered by the exporter's batching (see `OtelConfig`) — without this the last
   * window's worth is lost.
   */
  override def dispose(): Unit = service.close()
}
