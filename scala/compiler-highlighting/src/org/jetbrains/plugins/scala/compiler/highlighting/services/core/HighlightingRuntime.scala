package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import org.jetbrains.plugins.scala.compiler.highlighting.services.core.Planner.{PlanInput, PlannedWork}
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.{CompilationRequest, HighlightingRequest, RequestState}

/**
 * Everything a plan needs from the platform.
 */
private[highlighting] trait HighlightingRuntime:

  /** Read the current state. */
  def state: CompilationState

  /** Resolves the build target and the worksheet flag for each file. */
  def inputsFor(requests: Iterable[HighlightingRequest]): Iterable[PlanInput]
  /**
   * Whether this work can run right now.
   */
  def readiness(work: PlannedWork): RequestState

  /** Turns planned work into something executable, or nothing when it resolves to nothing. */
  def build(work: PlannedWork): Option[CompilationRequest]

  /** Repaints the diagnostics already held for these files. */
  def serveRecorded(files: Set[HighlightingRequest]): Unit