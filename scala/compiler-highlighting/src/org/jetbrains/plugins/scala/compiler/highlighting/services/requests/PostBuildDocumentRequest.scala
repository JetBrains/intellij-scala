package org.jetbrains.plugins.scala.compiler.highlighting.services.requests

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.highlighting.core.FileCompilationScope
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.RequestId

/**
 * A [[DocumentRequest]] issued right after a full incremental build has finished (see
 * `CompilerHighlightingService.triggerDocumentCompilationInAllOpenEditors`). It does not block the caller.
 *
 * Like any document compilation it reports the version of the document it compiles, so its results are drawn only
 * while the document still has that text.
 */
final class PostBuildDocumentRequest(
  scope: FileCompilationScope,
  debugReason: String,
  requestId: RequestId,
  project: Project,
  closeParent: Boolean = false
) extends DocumentRequest(scope, requestId, project, closeParent) {
  
  override protected def awaitCompletion: Boolean = false
}
