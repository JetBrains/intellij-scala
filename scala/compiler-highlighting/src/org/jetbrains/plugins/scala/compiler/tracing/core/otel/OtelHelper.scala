package org.jetbrains.plugins.scala.compiler.tracing.core.otel

import io.opentelemetry.api.common.Attributes
import org.jetbrains.plugins.scala.compiler.tracing.core.events.TraceEvent

/** Shared helper to translate TraceEvent args and category into OpenTelemetry Attributes. */
private[otel] object OtelHelper {
  def buildAttributes(event: TraceEvent): Attributes = {
    val builder = Attributes.builder()
    event.args.foreach { case (k, v) => builder.put(k, v) }
    event.category.foreach(c => builder.put("category", c))
    builder.build()
  }
}