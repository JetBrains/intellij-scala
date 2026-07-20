package org.jetbrains.plugins.scala.compiler.tracing.otel

import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.BatchSpanProcessor
import org.jetbrains.plugins.scala.compiler.tracing.core.otel.FileSpanExporter
import org.jetbrains.plugins.scala.compiler.tracing.core.{TraceFileWriter, TraceFormat}
import org.junit.Assert.*
import org.junit.{After, Before, Test}
import spray.json.*

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.compiletime.uninitialized

/**
 * End-to-end integration test of the OpenTelemetry file export pipeline.
 *
 * Verifies that OpenTelemetry spans are correctly batched by [[BatchSpanProcessor]],
 * processed by your custom [[FileSpanExporter]], and written to disk as Jaeger JSON 
 * via [[TraceFileWriter]].
 */
class OtelFileExportIntegrationTest {

  private var file: Path = uninitialized
  private var tracerProvider: SdkTracerProvider = uninitialized
  private var openTelemetry: OpenTelemetrySdk = uninitialized

  @Before
  def setUp(): Unit = {
    file = Files.createTempFile("otel-trace", ".json")
    // Remove it so the tests can assert whether/when the writer creates the file.
    Files.delete(file)

    // Build the exact pipeline from your OtelConfig
    val writer = new TraceFileWriter(file, TraceFormat.JaegerUI)
    val fileExporter = new FileSpanExporter(writer)
    val processor = BatchSpanProcessor.builder(fileExporter).build()

    tracerProvider = SdkTracerProvider.builder()
      .addSpanProcessor(processor)
      .build()

    openTelemetry = OpenTelemetrySdk.builder()
      .setTracerProvider(tracerProvider)
      .build()
  }

  @After
  def tearDown(): Unit = {
    // Ensure the provider is closed so we don't leak background threads
    if (tracerProvider != null) {
      tracerProvider.close()
    }
    Files.deleteIfExists(file)
  }

  private def content(): String =
    new String(Files.readAllBytes(file), UTF_8)

  @Test
  def spansAreExportedAndWrittenToFileOnFlush(): Unit = {
    val tracer = openTelemetry.getTracer("test-tracer")

    // Emit standard OpenTelemetry spans
    val span1 = tracer.spanBuilder("parse").startSpan()
    span1.end()

    val span2 = tracer.spanBuilder("typecheck").startSpan()
    span2.end()

    // The BatchSpanProcessor buffers spans in memory. 
    // forceFlush() guarantees the buffer is pushed to your FileSpanExporter.
    tracerProvider.forceFlush().join(10, TimeUnit.SECONDS)

    assertTrue("file should exist after flush", Files.exists(file))
    assertTrue("file should not be empty", Files.size(file) > 0)

    val jsonText = content()

    // Verify it is strictly valid JSON
    val jsonAst = jsonText.parseJson
    assertTrue("JSON AST should be parsed successfully", jsonAst != null)

    // Verify the exporter correctly mapped our span names into the output
    assertTrue("JSON output should contain 'parse'", jsonText.contains("parse"))
    assertTrue("JSON output should contain 'typecheck'", jsonText.contains("typecheck"))
  }

  @Test
  def closeFlushesTheRemainingSpans(): Unit = {
    val tracer = openTelemetry.getTracer("test-tracer")

    val span = tracer.spanBuilder("only").startSpan()
    span.end()

    // Buffer hasn't hit capacity and hasn't been explicitly flushed
    assertFalse("File should not exist yet because batch isn't flushed", Files.exists(file))

    // Closing the SDK automatically flushes the remaining buffer
    tracerProvider.close()

    assertTrue("file should exist after close", Files.exists(file))
    assertTrue("JSON output should contain the final span", content().contains("only"))
  }
}