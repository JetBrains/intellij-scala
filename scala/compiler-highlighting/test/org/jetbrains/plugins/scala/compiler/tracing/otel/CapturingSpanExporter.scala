package org.jetbrains.plugins.scala.compiler.tracing.otel

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.`export`.SpanExporter
import io.opentelemetry.sdk.trace.data.SpanData

import java.util
import scala.jdk.CollectionConverters.*

/**
 * A `SpanExporter` that keeps every exported [[SpanData]] in memory, so OTel tests can assert on the
 * spans the SDK produced without needing the (unavailable) `opentelemetry-sdk-testing` artifact.
 *
 * Wire it behind a `SimpleSpanProcessor` so spans are exported synchronously as they end.
 */
final class CapturingSpanExporter extends SpanExporter {

  private val captured = new util.ArrayList[SpanData]()

  def spans: List[SpanData] = synchronized(captured.asScala.toList)

  def byName(name: String): Option[SpanData] = spans.find(_.getName == name)

  override def `export`(spans: util.Collection[SpanData]): CompletableResultCode = {
    synchronized(captured.addAll(spans))
    CompletableResultCode.ofSuccess()
  }

  override def flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

  override def shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
}
