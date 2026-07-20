package org.jetbrains.plugins.scala.compiler.highlighting.events

import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.{BuildManagerSessionId, CompilationKind}
import org.jetbrains.plugins.scala.compiler.tracing.core.events.BaseEvent
import org.jetbrains.plugins.scala.util.CompilationId

import java.util.UUID

/**
 * Phase-2 (highlighting phase) spans, defined in flow order: everything from the compile server's first
 * reply onward (see [[TraceCategory]]).
 *
 * [[CompilationDurationEvent]] covers the server-side compilation (`CompilationStarted` →
 * `CompilationFinished`) and is handed off from the trigger-phase
 * [[TriggerPhaseEvents.CompilationRequestPhaseEvent]]. [[ExternalBuildEvent]] is its counterpart for
 * IDE-driven JPS builds. Once the compilation finishes, [[HighlightingEvent]] applies the results to the
 * open editors, with the per-problem [[RegisterQuickFixes]] / [[FindUnresolvedReferenceEvent]] markers
 * nested under it.
 */
object HighlightingPhaseEvents {
  
  /** Phase-2 span: the server-side compilation and the highlighting that follows (see
   * [[TraceCategory.Highlighting]]). */
  private[highlighting] class HighlightingPhaseEvent(name: String, parentKey: Option[Any], key: Option[Any],
                                                     closeParent: Boolean, args: (String, String)*) extends
    BaseEvent(name, parentKey, key, closeParent, false, args.toMap, Some(TraceCategory.Highlighting.toString))


  object CompilationDurationEvent {
    case class HighlightingPhaseID(compilationId: CompilationId)
    def key(compilationId: CompilationId): HighlightingPhaseID = HighlightingPhaseID(compilationId)
  }
  /**
   * The compilation span: from the first `CompilationStarted` received until `CompilationFinished` — i.e.
   * how long the compiler server spends actually compiling. Keyed by [[CompilationDurationEvent.key]] so the
   * editor highlighting can nest under it. Carries the same metadata as the
   * [[TriggerPhaseEvents.CompilationRequestPhaseEvent]] it is handed off from.
   */
  case class CompilationDurationEvent(kind: CompilationKind, file: String, reason: String, compilationId: CompilationId,
                                      closeRequest: Boolean = false) extends
    HighlightingPhaseEvent(name = s"${kind.toString} compilation", Some(compilationId),
      // closeParent MUST be true: the CompilationStarted handoff opens this span with the phase-1 request as
      // its parent, and it must consume (not peek) that request so it doesn't leak in the context registry.
      Some(CompilationDurationEvent.key(compilationId)), true,
      "kind" -> kind.toString, "file" -> file, "reason" -> reason, "id" -> compilationId.toString)

  /**
   * The highlighting-phase span for a `CompilationStarted` that has '''no''' preceding
   * [[TriggerPhaseEvents.CompilationRequestPhaseEvent]] — i.e. a build that didn't go through the CBH dispatch
   * sites. In practice this is the IDE's own JPS "Build Project" / automake, whose events reach the listener
   * via the custom-builder-message bridge (`CompilerEventFromCustomBuilderMessageListener`). No kind/file is
   * available at this point, but the `CompilationStarted` carries the JPS build `reason` ("Rebuild" for a
   * forced rebuild vs "Incremental") computed in `IdeClient`, plus the `module` from its `compilationUnitId`.
   * A project Rebuild compiles each module chunk separately, so it emits one span per module — the `module`
   * arg tells those spans apart.
   *
   * When `jpsSessionId` is present, the span nests under the [[TriggerPhaseEvents.BuildManagerSessionPhaseEvent]]
   * opened for that IDE build session (keyed by [[TriggerPhaseEvents.BuildManagerSessionId]]), so each
   * per-module external build shows inside its originating Build/Rebuild/automake session. It is `None` only
   * if the JPS-side session-id reflection failed, in which case the span becomes a root.
   */
  case class ExternalBuildEvent(reason: String, module: String, compilationId: CompilationId,
                                jpsSessionId: Option[UUID])
    extends HighlightingPhaseEvent("External build", jpsSessionId.map(BuildManagerSessionId.apply),
    Some(CompilationDurationEvent.key(compilationId)), false,
    "reason" -> reason, "module" -> module)

  /** Applying the compiler's highlighting results to the open editors, ending once the affected files
   *  have been updated. Consumes the [[CompilationDurationEvent.key]] entry from the duration span. */
  case class HighlightingEvent(compilationId: CompilationId, files: Set[String] = Set.empty)
    extends HighlightingPhaseEvent("Highlighting process", Some(CompilationDurationEvent.key(compilationId)),
      Some(CompilationDurationEvent.key(compilationId)), true,
      "Modified files" -> files.mkString(","))

  /** Registering the quick fixes offered for a highlighted problem. */
  case class RegisterQuickFixes(compilationId: CompilationId, fixes: Set[String] = Set.empty)
    extends HighlightingPhaseEvent("Register quick fixes", Some(CompilationDurationEvent.key(compilationId)),
      None, false,
      "Quick fixes" -> fixes.mkString(","))

  /** Resolving the quick fixes for an unresolved reference in a file. */
  case class FindUnresolvedReferenceEvent(compilationId: CompilationId, file: String)
    extends HighlightingPhaseEvent("Find unresolved reference", Some(CompilationDurationEvent.key(compilationId)),
      None, false,
      "Modified file" -> file)
}
