package org.jetbrains.plugins.scala.compiler.tracing.otel

import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.data.SpanData
import org.jetbrains.plugins.scala.compiler.tracing.core.otel.FileSpanExporter
import org.jetbrains.plugins.scala.compiler.tracing.core.{TraceFileWriter, TraceFormat}
import org.junit.Assert.*
import org.junit.{After, Before, Test}
import spray.json.*

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

class FileSpanExporterTest {

  private var file: Path = uninitialized

  @Before
  def setUp(): Unit = {
    file = Files.createTempFile("file-span-exporter", ".json")
    Files.delete(file) // let the writer create it, so we can assert it is/ isn't created
  }

  @After
  def tearDown(): Unit =
    Files.deleteIfExists(file)

  /** Produces real `SpanData`: a parent and a child (with one attribute and one span event). */
  private def sampleSpans(): List[SpanData] = {
    val capturing = new CapturingSpanExporter
    val provider = SdkTracerProvider.builder()
      .addSpanProcessor(SimpleSpanProcessor.create(capturing))
      .build()
    try {
      val tracer = provider.get("t")
      val parent = tracer.spanBuilder("parent").startSpan()
      val child = tracer.spanBuilder("child")
        .setParent(Context.root().`with`(parent))
        .startSpan()
      child.setAttribute("file", "Foo.scala")
      child.addEvent("rescheduled")
      child.end()
      parent.end()
      capturing.spans
    } finally provider.close()
  }

  /** Exports `spans` through the real [[FileSpanExporter]] and parses the emitted entries by name. */
  private def exportAndParse(spans: List[SpanData]): Map[String, JsObject] = {
    val writer = new TraceFileWriter(file, TraceFormat.PlainText)
    val exporter = new FileSpanExporter(writer)
    val result = exporter.`export`(spans.asJava)
    assertTrue("export should succeed", result.isSuccess)

    // Read the line-delimited trace objects emitted by the exporter
    val lines = new String(Files.readAllBytes(file), UTF_8)
      .split("\n")
      .filter(_.trim.nonEmpty)
      .toList

    // Extract the inner span objects from each trace object's "spans" array
    val spanObjects = lines.flatMap { line =>
      val traceObj = line.parseJson.asJsObject
      traceObj.fields("spans").asInstanceOf[JsArray].elements.map(_.asJsObject)
    }

    // Map operationName ("parent", "child") -> span JsObject
    spanObjects
      .map(o => o.fields("operationName").asInstanceOf[JsString].value -> o)
      .toMap
  }

  @Test
  def emptyBatchSucceedsWithoutCreatingTheFile(): Unit = {
    val writer = new TraceFileWriter(file, TraceFormat.PlainText)
    val exporter = new FileSpanExporter(writer)

    val result = exporter.`export`(new util.ArrayList[SpanData]())
    assertTrue(result.isSuccess)
    assertFalse("no file should be created for an empty batch", Files.exists(file))
  }

  @Test
  def groupsSpansByTraceIdAndIncludesProcesses(): Unit = {
    val spans = sampleSpans()
    val writer = new TraceFileWriter(file, TraceFormat.PlainText)
    val exporter = new FileSpanExporter(writer)
    exporter.`export`(spans.asJava)

    val lines = new String(Files.readAllBytes(file), UTF_8).split("\n").filter(_.trim.nonEmpty)

    // Both sample spans belong to the same Trace ID, so they should be grouped into 1 trace line
    assertEquals("Spans with the same Trace ID should be grouped together", 1, lines.length)

    val traceBlock = lines.head.parseJson.asJsObject
    assertTrue("Trace block should have a traceID", traceBlock.fields.contains("traceID"))
    assertTrue("Trace block should have spans array", traceBlock.fields.contains("spans"))
    assertTrue("Trace block should have processes object", traceBlock.fields.contains("processes"))

    val innerSpans = traceBlock.fields("spans").asInstanceOf[JsArray].elements
    assertEquals("Both spans should be inside the array", 2, innerSpans.length)

    val p1 = traceBlock.fields("processes").asJsObject.fields("p1").asJsObject
    assertEquals(JsString("scala-compiler-plugin"), p1.fields("serviceName"))
  }

  @Test
  def mapsCoreFieldsForEverySpan(): Unit = {
    val byName = exportAndParse(sampleSpans())

    List("parent", "child").foreach { name =>
      val obj = byName(name)
      assertTrue(s"$name should have a traceID", obj.fields("traceID").asInstanceOf[JsString].value.nonEmpty)
      assertTrue(s"$name should have a spanID", obj.fields("spanID").asInstanceOf[JsString].value.nonEmpty)
      assertEquals(JsString("p1"), obj.fields("processID"))
      assertTrue(s"$name should have a startTime", obj.fields.contains("startTime"))
      assertTrue(s"$name duration >= 1", obj.fields("duration").asInstanceOf[JsNumber].value.toLong >= 1L)
    }
  }

  @Test
  def emitsAChildOfReferenceForAParentedSpanOnly(): Unit = {
    val byName = exportAndParse(sampleSpans())

    assertFalse(byName("parent").fields.contains("references"))

    val refs = byName("child").fields("references").asInstanceOf[JsArray].elements
    assertEquals(1, refs.size)
    val ref = refs.head.asJsObject
    assertEquals(JsString("CHILD_OF"), ref.fields("refType"))
    assertEquals(byName("parent").fields("spanID"), ref.fields("spanID"))
    assertEquals(byName("child").fields("traceID"), ref.fields("traceID"))
  }

  @Test
  def translatesAttributesIntoTags(): Unit = {
    val byName = exportAndParse(sampleSpans())
    val tags = byName("child").fields("tags").asInstanceOf[JsArray].elements.map(_.asJsObject)

    val fileTag = tags.find(_.fields("key") == JsString("file"))
    assertTrue("the 'file' attribute should become a tag", fileTag.isDefined)
    assertEquals(JsString("Foo.scala"), fileTag.get.fields("value"))
    assertEquals(JsString("string"), fileTag.get.fields("type"))
  }

  @Test
  def translatesSpanEventsIntoLogs(): Unit = {
    val byName = exportAndParse(sampleSpans())
    val logs = byName("child").fields("logs").asInstanceOf[JsArray].elements.map(_.asJsObject)

    assertEquals(1, logs.size)
    val logFields = logs.head.fields("fields").asInstanceOf[JsArray].elements.map(_.asJsObject)
    val eventField = logFields.find(_.fields("key") == JsString("event"))
    assertTrue(eventField.isDefined)
    assertEquals(JsString("rescheduled"), eventField.get.fields("value"))
  }
}