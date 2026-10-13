package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.{RequestId, newRequestId}
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.CompilationDecision.{Document, Incremental}
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.Planner.{Plan, PlanInput}
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.HighlightingRequest

trait Planner:
  /**
   * Decides what to do about `inputs`, most preferred first.
   *
   * One state is used for every input on purpose. Deciding file by file would read a fresh state each
   * time, and two files can only both answer [[CompilationDecision.Document]] if they saw different
   * states, since that answer requires the file to be the only unbuilt one anywhere. The pair would then
   * be two compilations that were each individually wrong.
   */
  def plan(state: CompilationState, inputs: Iterable[PlanInput]): Plan

object Planner:
  def apply(): Planner = (state: CompilationState, inputs: Iterable[PlanInput]) =>
    val decided = inputs.map(input => input -> state.decide(input.target, input.request.file))
    val (recorded, pending) = decided.partition((input, decision) => decision.servedFromStore(input.isWorksheet))
  
    Plan(
      serveRecorded = recorded.map((input, _) => input.request),
      work = buildWork(state, pending)
    )
  
  /**
   * One file somebody wants highlighted, as the planner sees it.
   *
   * The target and the worksheet flag are resolved by the caller, because both need a read action and the
   * planner is deliberately free of the platform.
   */
  final case class PlanInput(request: HighlightingRequest,
                             target: Option[ModuleKey],
                             isWorksheet: Boolean)
  /** What kind of compilation a unit of planned work needs. */
  enum WorkKind:
    case Incremental(scope: Set[VirtualFile])
    case Document
    case Worksheet(needsModuleBuild: Boolean)
  
  /**
   * A unit of work the planner settled on, and the requests it answers for.
   *
   * `covers` holds the files that were '''asked about''' and whose answer this work provides, never the
   * files it happens to compile. A build over `{X, F1, F2}` prompted by asking about `F1` and `F2` covers
   * only those two: nobody asked about `X`, and forgetting that somebody wants `X` highlighted would throw
   * away the only record of it there is.
   */
  final case class PlannedWork(kind: WorkKind,
                               covers: ::[PlanInput],
                               id: RequestId)

  /**
   * What to do about the files that are waiting for an answer.
   *
   * `serveRecorded` are answered from the diagnostics already held, so their work is finished the moment
   * they are repainted. `work` is ordered by preference, and only the first item that can actually run is
   * handed over; the rest keep waiting and are planned again next time, against whatever that one did.
   */
  final case class Plan(serveRecorded: Iterable[HighlightingRequest], work: Iterable[PlannedWork])
  
  /**
   * Every source file that needs a build becomes one request, over the union of what each of them asked
   * for.
   *
   * The document answer (currently) requires the file to be the only unbuilt one
   * anywhere, so if anything else answered [[CompilationDecision.Incremental]] against the same state, the
   * document answer was already out of date, and the file is inside the build's scope by construction.
   * Nothing is lost either way, since a build document-compiles the open editors when it finishes.
   */
  private def buildWork(state: CompilationState, sources: Iterable[(PlanInput, CompilationDecision)]): Iterable[PlannedWork] =
    val generatedRequestId = newRequestId()
    val nonWorksheetsRequests = sources.filterNot(_._1.isWorksheet)
    val incrementalOrDocument = buildIncrementalOrDocument(state, nonWorksheetsRequests, generatedRequestId)
    val (incremental, document) = incrementalOrDocument.toList.partition { _.kind match
        case _: WorkKind.Incremental => true
        case _                       => false
    }

    val worksheet = buildWorksheet(sources, generatedRequestId)
    if incremental.isEmpty && worksheet.isEmpty
    then document
    else incremental ++ worksheet

  /**
   * Given a `state`, the rule states that there can be at most one file that is allowed to use the compiler.
   */
  private inline def buildIncrementalOrDocument(state: CompilationState, sources: Iterable[(PlanInput, CompilationDecision)],
                                      id: RequestId): Option[PlannedWork] =
    val incremental = sources.collect { case (input, Incremental(_)) => input }
    val document = sources.collectFirst { case (input, Document) => input }
    if incremental.nonEmpty then {
      val merged = (incremental ++ document).toList
      Some(PlannedWork(
        kind = WorkKind.Incremental(state.incrementalScope(merged.map(_.request.file).toSet)),
        covers = ::(merged.head, merged.tail),
        id = id
      ))
    } else document.map(input =>
      PlannedWork(WorkKind.Document, ::(input, Nil), id))

  /**
   * A worksheet is always its own request, never folded into a build.
   *
   * It carries its own preparatory build when it needs one, and that build covers the worksheet's module
   * alone. Two worksheets are therefore two requests.
   *
   * `needsModuleBuild` is only ever read by the work that is actually handed over, and a worksheet waiting
   * behind a build is planned again after it, so it sees what that build produced and usually stops asking
   * for one of its own.
   */
  private inline def buildWorksheet(sources: Iterable[(PlanInput, CompilationDecision)], id:RequestId): List[PlannedWork] =
    sources.filter(_._1.isWorksheet).map { (input, decision) =>
      PlannedWork(
        kind = WorkKind.Worksheet(needsModuleBuild = !decision.trustsBuildOutput),
        covers = ::(input, Nil),
        id = id
      )
    }.toList