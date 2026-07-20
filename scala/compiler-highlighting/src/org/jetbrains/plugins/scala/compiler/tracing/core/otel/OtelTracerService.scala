package org.jetbrains.plugins.scala.compiler.tracing.core.otel

import io.opentelemetry.api.trace.Tracer as OTelTracer
import io.opentelemetry.context.Context
import org.jetbrains.plugins.scala.compiler.tracing.core.TracerService
import org.jetbrains.plugins.scala.compiler.tracing.core.events.TraceEvent





/**
 * A [[TracerService]] backed by the OpenTelemetry SDK.
 * Uses explicit parenting via `Context.root().with(...)` to safely link cross-thread
 * asynchronous spans without relying on implicit ThreadLocal state.
 * Completely stateless—avoids memory leaks by returning self-contained span handles.
 *
 * @param onClose Shuts down the SDK pipeline behind `otelTracer`, writing out the spans it still buffers; run
 *                by [[close]]. Defaults to doing nothing, for a tracer whose pipeline is owned elsewhere.
 */
class OtelTracerService[T <: TraceEvent](otelTracer: OTelTracer, onClose: () => Unit = () => ())
  extends TracerService[T, OtelSpan[T]] {

  override def close(): Unit = onClose()

  override def traceEvent(event: T): Unit = {
    // For a standalone instant event, we create and immediately close a zero-duration span.
    otelTracer.spanBuilder(event.name)
      .setAllAttributes(OtelHelper.buildAttributes(event))
      .startSpan()
      .end()
  }

  override def begin(event: T, parent: Option[OtelSpan[T]]): OtelSpan[T] = {
    val builder = otelTracer.spanBuilder(event.name)
      .setAllAttributes(OtelHelper.buildAttributes(event))

    parent match {
      case Some(p) =>
        // Explicitly link to the provided parent span
        val explicitContext = Context.root().`with`(p.otelSpan)
        builder.setParent(explicitContext)

      case None => builder.setNoParent()
    }

    val otelSpan = builder.startSpan()

    val generatedId = System.identityHashCode(otelSpan).toLong

    OtelSpan(generatedId, event, otelSpan)
  }
}