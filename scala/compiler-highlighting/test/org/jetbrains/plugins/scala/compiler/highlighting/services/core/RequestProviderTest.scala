package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.RequestId
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.CompilationStateTestBase.CompilerHighlightingCoreTest
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.Planner.*
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.{CompilationKind, CompilationRequest, HighlightingRequest, RequestState}
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Tag, Test}

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.DurationInt

@Tag(CompilerHighlightingCoreTest)
class RequestProviderTest extends CompilationStateTestBase:

  @Test
  def testNext_SelectsHighestSequence_AmongMixedWorkKinds(): Unit =
    val app = module("app")
    val controller = app / "Controller.scala"
    val sheet = app / "Sheet.sc"

    // Mark project as built, then edit controller so it requires a build
    val stateWithEdit = empty
      .edit(controller)

    val reqIdBuild = RequestId(10L, 123456L)
    val reqIdSheet = RequestId(25L, 123456L) // Higher sequence on the worksheet

    val buildReq = createDummyRequest(controller, seq = 10, reqId = reqIdBuild)
    val sheetReq = createDummyRequest(sheet, seq = 25, reqId = reqIdSheet)

    val runtime = runtimeFor(
      initialState = stateWithEdit,
      requestState = RequestState.Ready,
      buildFn = work => Some(createDummyCompilationRequest(work.id, work.kind))
    )

    val provider = new RequestProvider(runtime, Planner())

    // Add both requests (1 produces Document work, 1 produces Worksheet work)
    provider.add(buildReq)
    provider.add(sheetReq)

    val result = provider.next()

    // Should prioritize the worksheet request because sequence 25 > sequence 10
    assertTrue(result.isDefined, "next() should return a RequestInfo")

    val (compilationReq, answeredReqs) = result.get
    assertEquals(25L, answeredReqs.headOption.fold(-1L)(_.sequence), "Should select request with highest sequence (25)")
    assertEquals(CompilationKind.Worksheet, compilationReq.kind, "Returned CompilationRequest should be worksheet")

  @Test
  def testNext_SelectsHighestSequence_WhenIncrementalBuildHasHigherSequence(): Unit =
    val app = module("app")
    val controller = app / "Controller.scala"
    val sheet = app / "Sheet.sc"

    val stateWithEdit = empty
      .edit(controller)

    val reqIdSheet = RequestId(10L, 123456L)
    val reqIdBuild = RequestId(50L, 123456L) // Higher sequence on the incremental build

    val sheetReq = createDummyRequest(sheet, seq = 10, reqId = reqIdSheet)
    val buildReq = createDummyRequest(controller, seq = 50, reqId = reqIdBuild)

    val runtime = runtimeFor(
      initialState = stateWithEdit,
      requestState = RequestState.Ready,
      buildFn = work => Some(createDummyCompilationRequest(work.id, work.kind))
    )

    val provider = new RequestProvider(runtime, Planner())
    provider.add(buildReq)
    provider.add(sheetReq)

    val result = provider.next()

    assertTrue(result.isDefined, "next() should return a RequestInfo")
    val (compilationReq, answeredReqs) = result.get
    assertEquals(50L, answeredReqs.headOption.fold(-1L)(_.sequence), "Should select request with highest sequence (50)")
    assertEquals(CompilationKind.JPSIncremental, compilationReq.kind, "Returned CompilationRequest should be incremental")

  @Test
  def testNext_LeavesUnselectedReadyRequestsInBucket(): Unit =
    val app = module("app")
    val controller = app / "Controller.scala"
    val sheet = app / "Sheet.sc"

    // Mark project as built, then edit controller so it requires a build
    val stateWithEdit = empty.edit(controller)

    val reqIdBuild = RequestId(10L, 123456L)
    val reqIdSheet = RequestId(25L, 123456L) // Higher sequence on the worksheet

    val buildReq = createDummyRequest(controller, seq = 10, reqId = reqIdBuild)
    val sheetReq = createDummyRequest(sheet, seq = 25, reqId = reqIdSheet)

    val runtime = runtimeFor(
      initialState = stateWithEdit,
      requestState = RequestState.Ready,
      buildFn = work => Some(createDummyCompilationRequest(work.id, work.kind))
    )

    val provider = new RequestProvider(runtime, Planner())

    // Add both requests. They will produce two distinct PlannedWork items.
    provider.add(buildReq)
    provider.add(sheetReq)

    // First pull: should take the Worksheet (sequence 25) and LEAVE the Controller (sequence 10)
    val firstResult = provider.next()
    assertTrue(firstResult.isDefined, "First next() should return a RequestInfo")

    val (firstCompReq, firstAnsweredReqs) = firstResult.get
    assertEquals(CompilationKind.Worksheet, firstCompReq.kind, "First result should be Worksheet (sequence 25)")
    assertEquals(Seq(sheetReq), firstAnsweredReqs, "Only the worksheet request should be returned")

    // Second pull: should now take the Controller (sequence 10) that was safely left behind
    val secondResult = provider.next()
    assertTrue(secondResult.isDefined, "Second next() should return the remaining RequestInfo")

    val (secondCompReq, secondAnsweredReqs) = secondResult.get
    assertEquals(CompilationKind.JPSIncremental, secondCompReq.kind, "Second result should be Incremental (sequence 10)")
    assertEquals(Seq(buildReq), secondAnsweredReqs, "Only the incremental request should be returned")

    // Third pull: the bucket should now be completely empty
    val thirdResult = provider.next()
    assertTrue(thirdResult.isEmpty, "Third next() should be empty")
    assertProviderEmpty(provider)

  @Test
  def testNext_DropsExpiredWork(): Unit =
    val app = module("app")
    val sheet = app / "Sheet.sc"

    val reqId = RequestId(10L, 123456L)
    val req = createDummyRequest(sheet, seq = 10, reqId = reqId)

    val runtime = runtimeFor(
      initialState = empty,
      requestState = RequestState.Expired,
      buildFn = _ => None
    )

    val provider = new RequestProvider(runtime, Planner(), 250.milliseconds)
    provider.add(req)

    val result = provider.next()

    assertTrue(result.isEmpty, "Should return None for expired work")
    assertProviderEmpty(provider)

  private def runtimeFor(
    initialState: CompilationState,
    requestState: RequestState,
    buildFn: PlannedWork => Option[CompilationRequest]
  ): HighlightingRuntime = new HighlightingRuntime:
    override def state: CompilationState = initialState

    override def inputsFor(reqs: Iterable[HighlightingRequest]): Iterable[PlanInput] =
      reqs.map: req =>
        PlanInput(req, targetOf(req.file), isWorksheet = req.file.getName.endsWith(".sc"))

    override def readiness(work: PlannedWork): RequestState = requestState

    override def build(work: PlannedWork): Option[CompilationRequest] = buildFn(work)

    /** Repaints the diagnostics already held for these files. */
    override def serveRecorded(files: Set[HighlightingRequest]): Unit = ()

  // we have to access a private field
  private def assertProviderEmpty(provider: RequestProvider): Unit =
    val heldField = classOf[RequestProvider].getDeclaredField("held")
    heldField.setAccessible(true)
    val heldRef = heldField.get(provider).asInstanceOf[AtomicReference[Map[VirtualFile, HighlightingRequest]]]
    assertTrue(heldRef.get().isEmpty, "Provider held map should be empty")

  private def createDummyRequest(vFile: VirtualFile, seq: Long, reqId: RequestId): HighlightingRequest =
    new HighlightingRequest:
      override val file: VirtualFile = vFile
      override val sequence: Long = seq
      override val id: RequestId = reqId
      override val debugReason: String = ""

  private def createDummyCompilationRequest(reqId: RequestId, workKind: WorkKind): CompilationRequest =
    new CompilationRequest:
      override def originFiles: Map[VirtualFile, com.intellij.openapi.editor.Document] = Map.empty
      override def id: RequestId = reqId
      override def documentVersions: Map[VirtualFile, org.jetbrains.plugins.scala.util.DocumentVersion] = Map.empty
      override def isReadyForExecution: RequestState = RequestState.Ready
      override private[services] def execute(): Unit = ()
      override def kind: CompilationKind = workKind match
        case WorkKind.Incremental(scope) => CompilationKind.JPSIncremental
        case WorkKind.Document => CompilationKind.Document
        case WorkKind.Worksheet(needsModuleBuild) => CompilationKind.Worksheet