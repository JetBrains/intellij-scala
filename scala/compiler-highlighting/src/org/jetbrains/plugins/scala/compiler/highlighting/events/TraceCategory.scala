package org.jetbrains.plugins.scala.compiler.highlighting.events

import org.jetbrains.plugins.scala.compiler.tracing.core.events.{BaseEvent, TraceEvent}

/**
 * The phase a trace event belongs to. Compiler-based highlighting has just '''two''' phases, split at the
 * moment the compile server first replies (the `CompilationStarted` event that opens a
 * [[HighlightingPhaseEvents.CompilationDurationEvent]]):
 *
 *  - [[TraceCategory.Trigger]] — everything '''before''' the `CompilationDurationEvent`: the highlighting
 *    trigger, the priority-queue wait, document saving, compile-server startup, lock acquisition and the
 *    "request → started" wait. It is the work done to get a request onto the server.
 *  - [[TraceCategory.Highlighting]] — everything '''from the server's reply onward''': the actual
 *    server-side compilation (`CompilationDurationEvent`) and the highlighting applied to the open editors
 *    once it finishes.
 *
 * The category is emitted as a span attribute, so a trace can be filtered
 * by phase.
 */

enum TraceCategory {
  /** Everything before the compilation duration: trigger, queue wait, document save, server startup, locks,
   * and the request → started wait. */
  case Trigger
  /** From the compile server's first reply onward: the server-side compilation and the highlighting applied
   * to the editors afterwards. */
  case Highlighting 
}