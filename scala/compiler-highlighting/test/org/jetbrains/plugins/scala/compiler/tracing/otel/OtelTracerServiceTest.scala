package org.jetbrains.plugins.scala.compiler.tracing.otel

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.{StatusCode, Tracer as OTelTracer}
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import org.jetbrains.plugins.scala.compiler.tracing.core.TraceSpan
import org.jetbrains.plugins.scala.compiler.tracing.core.events.TraceEvent
import org.jetbrains.plugins.scala.compiler.tracing.core.otel.{OtelSpan, OtelTracerService}
import org.junit.Assert.*
import org.junit.{After, Before, Test}

import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

object OtelTracerServiceTest {
  private final case class Event(name: String,
                                 override val args: Map[String, String] = Map.empty,
                                 cat: Option[String] = None) extends TraceEvent {
    override def category: Option[String] = cat
  }
}

/**
 * Unit tests for [[OtelTracerService]] and its [[OtelSpan]] handle, driven against a real
 * OpenTelemetry SDK whose spans are captured in memory by a [[CapturingSpanExporter]] wired through a
 * synchronous `SimpleSpanProcessor`. They verify the translation from our event model to OTel spans:
 * attributes, explicit parent linkage, error status, span events, and the standalone-instant shape.
 */
class OtelTracerServiceTest {

  import OtelTracerServiceTest.*

  private var provider: SdkTracerProvider = uninitialized
  private var exporter: CapturingSpanExporter = uninitialized
  private var service: OtelTracerService[Event] = uninitialized

  @Before
  def setUp(): Unit = {
    exporter = new CapturingSpanExporter
    provider = SdkTracerProvider.builder()
      .addSpanProcessor(SimpleSpanProcessor.create(exporter)) // exports synchronously on span end
      .build()
    val tracer: OTelTracer = provider.get("test-tracer")
    service = new OtelTracerService[Event](tracer)
  }

  @After
  def tearDown(): Unit =
    if (provider != null) provider.close()

  private def attr(spanName: String, key: String): Option[String] =
    exporter.byName(spanName).flatMap(s => Option(s.getAttributes.get(AttributeKey.stringKey(key))))

  @Test
  def beginThenEndExportsASpanWithItsArgsAndCategoryAsAttributes(): Unit = {
    val span = service.begin(Event("parse", Map("file" -> "Foo.scala"), cat = Some("highlighting")), None)
    span.end()

    assertTrue("span should have been exported", exporter.byName("parse").isDefined)
    assertEquals(Some("Foo.scala"), attr("parse", "file"))
    assertEquals(Some("highlighting"), attr("parse", "category"))
  }

  @Test
  def beginWithAnExplicitParentLinksTheChildToItInTheSameTrace(): Unit = {
    val parent = service.begin(Event("compilation"), None)
    val child = service.begin(Event("typecheck"), Some(parent))
    child.end()
    parent.end()

    val parentData = exporter.byName("compilation").get
    val childData = exporter.byName("typecheck").get

    assertEquals(parentData.getSpanId, childData.getParentSpanContext.getSpanId)
    assertEquals(parentData.getTraceId, childData.getTraceId) // same trace
  }

  @Test
  def beginWithoutAParentStartsANewRootTrace(): Unit = {
    val a = service.begin(Event("a"), None)
    val b = service.begin(Event("b"), None)
    a.end()
    b.end()

    assertNotEquals(exporter.byName("a").get.getTraceId, exporter.byName("b").get.getTraceId)
  }

  @Test
  def traceEventExportsAStandaloneSpan(): Unit = {
    service.traceEvent(Event("trigger", Map("source" -> "editor focus")))

    assertTrue(exporter.byName("trigger").isDefined)
    assertEquals(Some("editor focus"), attr("trigger", "source"))
    // Not linked to anything: a standalone instant is a root.
    assertFalse(exporter.byName("trigger").get.getParentSpanContext.isValid)
  }

  @Test
  def endWithErrorMarksTheSpanAsErrorAndRecordsTheErrorEvent(): Unit = {
    val span = service.begin(Event("compile"), None)
    span.endWithError(Event("boom"))

    val data = exporter.byName("compile").get
    assertEquals(StatusCode.ERROR, data.getStatus.getStatusCode)
    assertTrue("the error should be recorded as a span event",
      data.getEvents.asScala.exists(_.getName == "boom"))
  }

  @Test
  def traceEventOnAnOpenSpanIsRecordedAsASpanEventWithoutClosingIt(): Unit = {
    val span = service.begin(Event("queue wait"), None)
    span.traceEvent(Event("rescheduled"))
    // Not exported yet: the span is still open.
    assertTrue(exporter.byName("queue wait").isEmpty)

    span.end()
    val data = exporter.byName("queue wait").get
    assertTrue(data.getEvents.asScala.exists(_.getName == "rescheduled"))
  }

  @Test
  def withEventKeepsTheSameUnderlyingSpanButSwapsTheEventLabel(): Unit = {
    val span = service.begin(Event("request"), None)
    val remapped: TraceSpan[Event] = span.withEvent(Event("duration", Map("kind" -> "Worksheet")))

    assertEquals(span.id, remapped.id) // same underlying OTel span
    assertEquals("duration", remapped.event.name)

    remapped.end() // ending the remapped handle closes the one, shared OTel span
    val data = exporter.byName("request").get // name is fixed at start; the end args are merged in
    assertEquals("Worksheet", data.getAttributes.get(AttributeKey.stringKey("kind")))
  }

  @Test
  def idIsDerivedFromTheUnderlyingOtelSpanIdentity(): Unit = {
    val span = service.begin(Event("op"), None).asInstanceOf[OtelSpan[Event]]
    assertEquals(System.identityHashCode(span.otelSpan).toLong, span.id)
    span.end()
  }
}
