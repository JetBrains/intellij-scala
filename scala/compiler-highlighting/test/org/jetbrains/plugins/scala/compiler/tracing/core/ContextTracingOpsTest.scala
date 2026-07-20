package org.jetbrains.plugins.scala.compiler.tracing.core

import org.jetbrains.plugins.scala.compiler.tracing.core.events.{ContextTraceEvent, EndEvent, EventContext, TraceEvent}
import org.junit.Assert.*
import org.junit.{Before, Test}

import scala.compiletime.uninitialized

object ContextTracingOpsTest {
  private type Event = ContextTraceEvent

  /** A configurable [[EventContext]] event so each test can set `key` / `parentKey` / `closeParent` /
   *  `closeOnEnd`. */
  private final case class Ctx(name: String,
                               key: Option[Any] = None,
                               parentKey: Option[Any] = None,
                               closeParent: Boolean = false,
                               closeOnEnd: Boolean = false,
                               override val args: Map[String, String] = Map.empty,
                               override val category: Option[String] = None)
    extends ContextTraceEvent
}

/**
 * Unit tests for [[ContextTracingOps]], the ops layer that wires parent/child context propagation on
 * top of a [[TracerService]]. Every method is exercised for how it manages the private context
 * registry: resolving a parent for a new span, keeping keyed spans available for later children,
 * and — crucially — removing entries so the registry doesn't leak spans nobody will reference again.
 *
 * The registry is private, so it is observed indirectly through the `parent` each new [[RecordingSpan]]
 * is opened with: a resolvable parent means the entry is still present; `None` means it was removed.
 */
class ContextTracingOpsTest {

  import ContextTracingOpsTest.*

  private var service: RecordingTracerService[Event] = uninitialized
  private var ops: ContextTracingOps[RecordingSpan[Event]] = uninitialized

  @Before
  def setUp(): Unit = {
    service = new RecordingTracerService[Event]
    val lifecycle = new QueueRegistry[Any, TraceSpan[Event]]
    ops = new ContextTracingOps[RecordingSpan[Event]](service, lifecycle)
  }

  /** Opens a span and returns it as the concrete [[RecordingSpan]], so tests can read its `parent`. */
  private def begin(event: Event): RecordingSpan[Event] =
    ops.begin(event).asInstanceOf[RecordingSpan[Event]]

  // --- begin: parent resolution & registration ---

  @Test
  def beginWithoutAParentKeyOpensARootSpan(): Unit = {
    val span = begin(Ctx("root"))
    assertEquals(None, span.parent)
  }

  @Test
  def beginResolvesTheParentByParentKeyAndKeepsItForFurtherChildren(): Unit = {
    val parent = begin(Ctx("parent", key = Some("P")))

    val child1 = begin(Ctx("child1", parentKey = Some("P"), closeParent = false))
    val child2 = begin(Ctx("child2", parentKey = Some("P"), closeParent = false))

    // A non-closing child peeks the registry, so the parent stays available for the next child.
    assertEquals(Some(parent), child1.parent)
    assertEquals(Some(parent), child2.parent)
  }

  @Test
  def beginWithCloseParentRemovesTheParentAfterResolvingIt(): Unit = {
    val parent = begin(Ctx("parent", key = Some("P")))

    val child = begin(Ctx("child", parentKey = Some("P"), closeParent = true))
    assertEquals(Some(parent), child.parent) // resolved just before removal

    // closeParent did a destructive read: "P" is gone, so a later reference resolves nothing.
    val orphan = begin(Ctx("orphan", parentKey = Some("P")))
    assertEquals(None, orphan.parent)
  }

  @Test
  def beginRegistersItsOwnKeySoDescendantsCanNest(): Unit = {
    begin(Ctx("parent", key = Some("P")))
    val child = begin(Ctx("child", key = Some("C"), parentKey = Some("P")))

    val grandchild = begin(Ctx("grandchild", parentKey = Some("C")))
    assertEquals(Some(child), grandchild.parent)
  }

  @Test
  def beginWithAParentKeyThatIsNotRegisteredOpensARootSpan(): Unit = {
    val span = begin(Ctx("child", parentKey = Some("absent")))
    assertEquals(None, span.parent)
  }

  @Test
  def instantOpensAndImmediatelyClosesASpan(): Unit = {
    ops.instant(Ctx("trigger", args = Map("source" -> "editor focus")))

    service.ops match {
      case TraceOp.Began(bid, evt, _) :: TraceOp.Ended(eid, _) :: Nil =>
        assertEquals(bid, eid)
        assertEquals(Map("source" -> "editor focus"), evt.args)
      case other => fail(s"expected a begin immediately followed by an end, got: $other")
    }
  }

  @Test
  def instantResolvesItsParentLikeBegin(): Unit = {
    val parent = begin(Ctx("parent", key = Some("P")))
    ops.instant(Ctx("mark", parentKey = Some("P")))

    val instantBegin = service.begins.last
    assertEquals(Some(parent), instantBegin.parent)
  }

  @Test
  def instantRegistersItsOwnKeySoLaterChildrenCanReferenceIt(): Unit = {
    ops.instant(Ctx("mark", key = Some("M")))
    val child = begin(Ctx("child", parentKey = Some("M")))
    assertTrue(child.parent.isDefined)
  }


  /**
   * An [[EndEvent]] (`key = None`, `closeParent = true`) is the terminal
   * cleanup event. Recording it must destructively remove its parent from the context registry so the
   * parent span isn't retained forever, while the [[EndEvent]] itself — being keyless — leaves nothing
   * behind.
   */
  @Test
  def endEventRemovesItsParentFromContextLeavingNoLeak(): Unit = {
    val parent = begin(Ctx("session", key = Some("S")))

    ops.instant(EndEvent("S", "canceled"))

    val endBegin = service.begins.last
    assertEquals("End Event", endBegin.event.name)
    assertEquals(Some(parent), endBegin.parent) // resolved the parent it is closing
    assertEquals(Map("reason" -> "canceled"), endBegin.event.args)

    // The parent context entry is gone: nothing else can (accidentally) attach to "S" anymore.
    val after = begin(Ctx("after", parentKey = Some("S")))
    assertEquals(None, after.parent)
  }

  @Test
  def endEventClosesItsOwnSpanLeavingNothingOpen(): Unit = {
    begin(Ctx("session", key = Some("S")))
    ops.instant(EndEvent("S", "done"))

    // The EndEvent is emitted as an instant: its own span opens and closes, so it never lingers open.
    val endId = service.begins.last.id
    assertTrue("the EndEvent span must be closed",
      service.ops.exists { case TraceOp.Ended(id, _) => id == endId; case _ => false })
  }

  @Test
  def endOfASpanWithoutCloseOnEndKeepsItAvailableForFutureChildren(): Unit = {
    val parent = begin(Ctx("parent", key = Some("P"))) // closeOnEnd defaults to false
    ops.end(parent) // without closeOnEnd the key stays registered so later work can still nest under it

    val child = begin(Ctx("child", parentKey = Some("P")))
    assertEquals(Some(parent), child.parent)
  }

  @Test
  def endOfAKeylessSpanLeavesItsParentInPlace(): Unit = {
    val parent = begin(Ctx("parent", key = Some("P")))
    val leaf = begin(Ctx("leaf", parentKey = Some("P"), closeParent = false)) // peeked -> P stays
    assertEquals(Some(parent), leaf.parent)

    ops.end(leaf) // leaf does not request closeOnEnd, so ending it touches nothing in the registry

    // The parent is removed only when its own span ends with closeOnEnd (or a closeParent child consumes
    // it), never by a sibling/child ending — so "P" is still resolvable here.
    val sibling = begin(Ctx("sibling", parentKey = Some("P")))
    assertEquals(Some(parent), sibling.parent)
  }

  @Test
  def endWithCloseOnEndRemovesTheSpansOwnKeyFromContext(): Unit = {
    val span = begin(Ctx("keyed", key = Some("K"), closeOnEnd = true))

    ops.end(span) // closeOnEnd = true -> the span drops its own key "K" when it ends

    val orphan = begin(Ctx("after", parentKey = Some("K")))
    assertEquals(None, orphan.parent)
  }

  @Test
  def closedMarksASpanToRemoveItsOwnKeyWhenEnded(): Unit = {
    val span = begin(Ctx("keyed", key = Some("K"))) // closeOnEnd defaults to false

    // `.closed()` sets closeOnEnd, so mapping the span through it and ending drops its own key "K".
    ops.mapAndEnd(span)(e => Some(e.closed()))

    val orphan = begin(Ctx("after", parentKey = Some("K")))
    assertEquals(None, orphan.parent)
  }

  @Test
  def endOfAKeyedSpanAlsoActuallyClosesTheUnderlyingSpan(): Unit = {
    val span = begin(Ctx("parent", key = Some("P")))
    ops.end(span)
    assertTrue("the underlying span must be closed", span.ended)
  }

  @Test
  def keyedBeginAndEndPairThroughTheLifecycleRegistry(): Unit = {
    ops.begin("life-1", Ctx("keyed", key = Some("K")))
    ops.end("life-1")

    // begin/end pair by the lifecycle key and the span is closed.
    val ended = service.ops.collect { case e @ TraceOp.Ended(_, _) => e }
    assertEquals(1, ended.size)
    assertEquals("keyed", ended.head.event.name)
  }
}
