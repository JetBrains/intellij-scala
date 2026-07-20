package org.jetbrains.plugins.scala.compiler.tracing.core.otel

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.`export`.SpanExporter
import org.jetbrains.plugins.scala.compiler.tracing.core.TraceFileWriter
import spray.json._

import java.util
import scala.jdk.CollectionConverters._

class FileSpanExporter(writer: TraceFileWriter) extends SpanExporter {

  override def `export`(spans: util.Collection[SpanData]): CompletableResultCode = {
    if (spans.isEmpty) return CompletableResultCode.ofSuccess()

    try {
      // Group spans by their actual Trace ID
      val spansByTrace = spans.asScala.groupBy(_.getTraceId)

      val traceEntries = spansByTrace.map { case (traceId, traceSpans) =>
        val spanJsons = traceSpans.map(spanToJson).toVector

        JsObject(
          "traceID" -> JsString(traceId),
          "spans" -> JsArray(spanJsons),
          "processes" -> JsObject(
            "p1" -> JsObject(
              "serviceName" -> JsString("scala-compiler-plugin"),
              "tags" -> JsArray()
            )
          )
        ).compactPrint
      }.toSeq

      writer.append(traceEntries)
      CompletableResultCode.ofSuccess()
    } catch {
      case _: Throwable => CompletableResultCode.ofFailure()
    }
  }
  
  override def flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

  /** No resources to release: [[TraceFileWriter]] opens and closes a channel per append. */
  override def shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()

  /**
   * Maps an OpenTelemetry SpanData object directly into the Jaeger UI JSON Schema.
   */
  private def spanToJson(span: SpanData): JsValue = {
    val fields = List.newBuilder[(String, JsValue)]

    fields += "traceID" -> JsString(span.getTraceId)
    fields += "spanID" -> JsString(span.getSpanId)
    fields += "operationName" -> JsString(span.getName)
    fields += "processID" -> JsString("p1")

    val startMicros = span.getStartEpochNanos / 1000L
    val endMicros = span.getEndEpochNanos / 1000L
    fields += "startTime" -> JsNumber(startMicros)
    fields += "duration" -> JsNumber(Math.max(1L, endMicros - startMicros))

    // Parent-Child relationship
    if (span.getParentSpanContext.isValid) {
      val ref = JsObject(
        "refType" -> JsString("CHILD_OF"),
        "traceID" -> JsString(span.getTraceId),
        "spanID" -> JsString(span.getParentSpanContext.getSpanId)
      )
      fields += "references" -> JsArray(ref)
    }

    // Attributes map to Jaeger "Tags"
    val attributes = span.getAttributes.asMap().asScala
    if (attributes.nonEmpty) {
      val tags = attributes.map { case (k, v) =>
        JsObject(
          "key" -> JsString(k.getKey),
          "type" -> JsString("string"),
          "value" -> JsString(v.toString)
        )
      }.toVector
      fields += "tags" -> JsArray(tags)
    }

    // Span Events map to Jaeger "Logs"
    val events = span.getEvents.asScala
    if (events.nonEmpty) {
      val logs = events.map { event =>
        JsObject(
          "timestamp" -> JsNumber(event.getEpochNanos / 1000L),
          "fields" -> JsArray(JsObject(
            "key" -> JsString("event"),
            "type" -> JsString("string"),
            "value" -> JsString(event.getName)
          ))
        )
      }.toVector
      fields += "logs" -> JsArray(logs)
    }

    JsObject(fields.result().toMap)
  }
}