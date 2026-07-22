package org.jetbrains.plugins.scala.compiler.highlighting.services.requests

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.highlighting.core.FileCompilationScope
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.RequestId
import org.jetbrains.plugins.scala.util.CanonicalPath

import scala.collection.immutable.HashMap
import scala.concurrent.duration.Deadline

/**
 * A [[DocumentRequest]] issued right after a full incremental build has finished (see
 * `CompilerHighlightingService.triggerDocumentCompilationInAllOpenEditors`). Because a build just completed,
 * its results are applied unconditionally (no document versions are reported) and it does not block the caller.
 *
 */
final class PostBuildDocumentRequest(
  scope: FileCompilationScope,
  debugReason: String,
  deadline: Deadline,
  requestId: RequestId,
  project: Project
) extends DocumentRequest(scope, debugReason, deadline, requestId, project) {

  /**
   * Never scheduled, so never delayed. Overridden because the inherited `copy` would return a plain
   * [[DocumentRequest]], silently dropping the shared client and starting a compilation of its own.
   */
  override def delayed(newDeadline: Deadline): DocumentRequest = this
  override protected def awaitCompletion: Boolean = false

  override protected def reportedDocumentVersions: SerializableMap[CanonicalPath, Long] =
    HashMap.empty[CanonicalPath, Long]
}
