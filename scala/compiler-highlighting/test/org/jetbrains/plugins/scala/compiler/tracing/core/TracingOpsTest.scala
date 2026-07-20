package org.jetbrains.plugins.scala.compiler.tracing.core

import org.jetbrains.plugins.scala.compiler.tracing.core.events.TraceEvent
import org.junit.Assert.*
import org.junit.{Before, Test}

import scala.compiletime.uninitialized

object TracingOpsTest {
  private final case class TestEvent(name: String, override val args: Map[String, String] = Map.empty,
                                     override val category: Option[String] = None)
    extends TraceEvent
}

/**
 * Unit tests for the derived [[TracingOps]] facade (the `BaseTracingOps` built by [[TracingOps.apply]]),
 * driven against a [[RecordingTracerService]] and a real [[QueueRegistry]]. They assert that each
 * derived operation (`trace`, keyed begin/end, `mark`, `map`, `mapAndEnd`, `handoff`, `carry`) issues
 * exactly the right primitive calls to the service, in the right order.
 */
class TracingOpsTest {

  import TracingOpsTest.*

  private var service: RecordingTracerService[TraceEvent] = uninitialized
  private var ops: TracingOps[TraceEvent] = uninitialized

  @Before
  def setUp(): Unit = {
    service = new RecordingTracerService[TraceEvent]
    val registry = new QueueRegistry[Any, TraceSpan[TraceEvent]]
    ops = TracingOps(service, registry)
  }

  @Test
  def beginOpensASpanWithNoParentAndEndClosesIt(): Unit = {
    val span = ops.begin(TestEvent("compile"))
    ops.end(span)

    service.ops match {
      case TraceOp.Began(bid, be, parent) :: TraceOp.Ended(eid, _) :: Nil =>
        assertEquals("compile", be.name)
        assertEquals(None, parent) // handle-based begin never links a parent
        assertEquals(bid, eid) // begin/end pair by id
      case other => fail(s"unexpected ops: $other")
    }
  }

  @Test
  def traceRunsTheBlockReturnsTheResultAndRecordsBeginThenEnd(): Unit = {
    val result = ops.trace(TestEvent("worksheet")) {
      1 + 1
    }

    assertEquals(2, result)
    assertEquals(2, service.ops.size)
    assertTrue(service.ops.head.isInstanceOf[TraceOp.Began[?]])
    assertTrue(service.ops(1).isInstanceOf[TraceOp.Ended[?]])
  }

  @Test
  def traceEndsTheSpanEvenWhenTheBlockThrows(): Unit = {
    val rethrown =
      try { ops.trace(TestEvent("boom"))(throw new RuntimeException("x")); false }
      catch { case _: RuntimeException => true }

    assertTrue("trace must not swallow the block's exception", rethrown)
    assertEquals(2, service.ops.size)
    assertTrue(service.ops(1).isInstanceOf[TraceOp.Ended[?]])
  }

  @Test
  def instantRecordsAStandaloneEventNotAttachedToAnySpan(): Unit = {
    ops.instant(TestEvent("trigger", Map("source" -> "editor focus")))

    service.ops match {
      case TraceOp.Instant(e) :: Nil => assertEquals(Map("source" -> "editor focus"), e.args)
      case other => fail(s"expected a single standalone instant, got: $other")
    }
  }

  @Test
  def markOnAHandleRecordsAnEventWithoutClosingTheSpan(): Unit = {
    val span = ops.begin(TestEvent("op"))
    ops.mark(span, TestEvent("rescheduled"))

    service.ops match {
      case TraceOp.Began(bid, _, _) :: TraceOp.Marked(mid, me) :: Nil =>
        assertEquals(bid, mid) // mark sits on the open span
        assertEquals("rescheduled", me.name)
      case other => fail(s"unexpected ops: $other")
    }
  }

  @Test
  def keyedBeginAndEndPairByTheSharedKeyInFifoOrder(): Unit = {
    ops.begin("k", TestEvent("first"))
    ops.begin("k", TestEvent("second"))
    ops.end("k")
    ops.end("k")

    val ends = service.ops.collect { case e @ TraceOp.Ended(_, _) => e }
    assertEquals(2, ends.size)
    assertEquals("first", ends.head.event.name) // FIFO: first opened is first closed
    assertEquals("second", ends(1).event.name)
  }

  @Test
  def endByKeyIsANoOpWhenNoSpanIsOpen(): Unit = {
    ops.end("never-begun")
    assertTrue(service.ops.isEmpty)
  }

  @Test
  def markByKeyPeeksTheSpanWithoutRemovingIt(): Unit = {
    ops.begin("k", TestEvent("op"))
    ops.mark("k", TestEvent("m"))
    ops.end("k") // still closable afterwards -> mark peeked, did not pop

    assertEquals(1, service.ops.count(_.isInstanceOf[TraceOp.Marked[?]]))
    assertEquals(1, service.ops.count(_.isInstanceOf[TraceOp.Ended[?]]))
  }

  @Test
  def mapAndEndByKeyEnrichesTheEventAndClosesTheSpan(): Unit = {
    ops.begin("highlighting-1", TestEvent("highlighting"))
    ops.mapAndEnd("highlighting-1") {
      case e: TestEvent => Some(e.copy(args = e.args + ("files" -> "A.scala")))
      case other => Some(other)
    }

    val ended = service.ops.collectFirst { case e @ TraceOp.Ended(_, _) => e }
    assertTrue("the span must be closed", ended.isDefined)
    assertEquals(Map("files" -> "A.scala"), ended.get.event.args) // enrichment carried onto the end
  }

  @Test
  def mapByKeyEnrichesTheOpenSpanWithoutClosingIt(): Unit = {
    ops.begin("k", TestEvent("e"))
    ops.map("k") {
      case e: TestEvent => Some(e.copy(args = e.args + ("added" -> "1")))
      case other => Some(other)
    }
    assertTrue("map alone must not end the span", !service.ops.exists(_.isInstanceOf[TraceOp.Ended[?]]))

    ops.end("k")
    val ended = service.ops.collectFirst { case e @ TraceOp.Ended(_, _) => e }.get
    assertEquals(Map("added" -> "1"), ended.event.args)
  }

  @Test
  def handoffEndsTheFromSpanAndOpensTheToSpanCarryingMetadata(): Unit = {
    ops.begin("request-1", TestEvent("request", Map("kind" -> "Worksheet")))
    ops.handoff("request-1", "1") {
      case e: TestEvent => TestEvent("duration", e.args)
      case other => other
    }
    ops.end("1")

    val names = service.ops.map(_.event.name)
    // the "to" span opens before the "from" span closes (so a flow arrow could bind)
    assertEquals(List("request", "duration", "request", "duration"), names)

    val durationBegin = service.begins.find(_.event.name == "duration").get
    assertEquals(Map("kind" -> "Worksheet"), durationBegin.event.args) // metadata carried across
  }

  @Test
  def handoffWithFallbackOpensTheToSpanWhenFromIsAbsent(): Unit = {
    ops.handoff("request-1", "1", TestEvent("external build", Map("reason" -> "Rebuild"))) {
      case e: TestEvent => TestEvent("duration", e.args)
      case other => other
    }
    ops.end("1")

    service.ops match {
      case TraceOp.Began(bid, be, _) :: TraceOp.Ended(eid, _) :: Nil =>
        assertEquals("external build", be.name)
        assertEquals(Map("reason" -> "Rebuild"), be.args)
        assertEquals(bid, eid)
      case other => fail(s"unexpected ops: $other")
    }
  }

  @Test
  def carryMovesTheOpenSpanToANewKeyKeepingItOpen(): Unit = {
    ops.begin("k1", TestEvent("op"))
    ops.carry("k1", "k2")

    assertEquals("carry must not close the span", 1, service.ops.size)

    ops.end("k1") // nothing under the old key anymore
    assertEquals(1, service.ops.size)

    ops.end("k2") // closes from the new key, pairing with the original begin
    assertEquals(2, service.ops.size)
    assertTrue(service.ops(1).isInstanceOf[TraceOp.Ended[?]])
  }
}
