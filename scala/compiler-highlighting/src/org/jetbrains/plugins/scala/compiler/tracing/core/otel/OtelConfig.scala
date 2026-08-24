package org.jetbrains.plugins.scala.compiler.tracing.core.otel

import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.BatchSpanProcessor
import org.jetbrains.plugins.scala.compiler.tracing.core.events.ContextTraceEvent
import org.jetbrains.plugins.scala.compiler.tracing.core.{TraceFileWriter, TraceFormat, TracerService}

import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

object OtelConfig {

  /**
   *
   * [[FileSpanExporter]] writes one Jaeger entry per (batch, trace), so a parent and a child that end in
   * different batches are written as two entries carrying the same `traceID` — which the Jaeger UI lists as
   * two traces, the child one root-less.
   *
   * A long schedule delay plus a batch and queue large enough not to force an early export therefore keep a
   * whole trace inside one batch for the common case, and `TracerService.close` writes out whatever is still
   * buffered when the project closes. It stays a heuristic: a trace that outlives the delay still splits, so
   * entries sharing a `traceID` must be merged when reading.
   */
  private val ScheduleDelay = Duration.ofSeconds(60)
  /**
   * The processor exports as soon as this many spans are queued, whatever [[ScheduleDelay]] says.
   */
  private val MaxExportBatchSize = 2000
  /** Ended spans buffered before export; anything over this is dropped silently, so it is kept well above
   * [[MaxExportBatchSize]]. */
  private val MaxQueueSize = 6000
  /** How long the flush on project close may block before it is given up on. */
  private val ShutdownTimeout = Duration.ofSeconds(5)

  def service(path: Path): TracerService[ContextTraceEvent, OtelSpan[ContextTraceEvent]] = {

    val writer = new TraceFileWriter(path, TraceFormat.JaegerUI)
    val fileExporter = new FileSpanExporter(writer)
    val fileProcessor = BatchSpanProcessor.builder(fileExporter)
      .setScheduleDelay(ScheduleDelay)
      .setMaxExportBatchSize(MaxExportBatchSize)
      .setMaxQueueSize(MaxQueueSize)
      .build()

    val tracerProvider = SdkTracerProvider.builder()
      .addSpanProcessor(fileProcessor)
      .build()

    val openTelemetry = OpenTelemetrySdk.builder()
      .setTracerProvider(tracerProvider)
      .build()


    lazy val tracer = openTelemetry.getTracer("scala-plugin-tracer")
    // Shutting the SDK down drains the batch processor through the exporter, so the spans buffered since the
    // last export still reach the file when the project (or the IDE) closes. The wait is bounded because the
    // caller may run it on the EDT.
    val flushAndShutdown = () => {
      openTelemetry.shutdown().join(ShutdownTimeout.toMillis, TimeUnit.MILLISECONDS)
      ()
    }
    new OtelTracerService[ContextTraceEvent](tracer, flushAndShutdown)
  }
}
