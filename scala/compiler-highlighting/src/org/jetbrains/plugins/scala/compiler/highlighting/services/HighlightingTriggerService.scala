package org.jetbrains.plugins.scala.compiler.highlighting.services

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.scala.compiler.highlighting.compilation.CompilationDecision
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.{HighlightingTriggerPhaseEvent, RecordedDiagnosticsServedEvent}
import org.jetbrains.plugins.scala.compiler.highlighting.services.BackgroundExecutorService.executeOnBackgroundThreadInNotDisposed
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.ScheduledDebouncer
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.CompilationRequest
import org.jetbrains.plugins.scala.compiler.highlighting.services.{CompilationLogicService, CompilerHighlightingService, ExternalHighlightersService}
import org.jetbrains.plugins.scala.compiler.highlighting.triggers.TriggerUtil
import org.jetbrains.plugins.scala.compiler.highlighting.util.TracingUtil.endTrace
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode

@Service(Array(Service.Level.PROJECT))
final class HighlightingTriggerService(project: Project):

  private[highlighting] inline def DELAY = ScalaHighlightingMode.compilationDelay(project)

  private val debouncer = new ScheduledDebouncer[VirtualFile](
    DELAY,
    AppExecutorUtil.createBoundedScheduledExecutorService("HighlightingTriggerDebouncer", 1),
    project
  )
  private val logic = CompilationLogicService(project)
  private val tracer = Tracing(project)
  private val service = CompilerHighlightingService.get(project)
  private val Log = Logger.getInstance(classOf[HighlightingTriggerService])
  private val highlighters = ExternalHighlightersService(project)

  /**
   * Enqueues a compilation request behind a debounce delay.
   *
   * Use for high-frequency events, such as document edits, where subsequent events
   * rapidly invalidate the current request. The compilation is dispatched only after
   * the event stream pauses.
   */
  def schedule(file: VirtualFile, reason: String): Unit =
    // we can avoid scheduling by doing some pre-checks
    if (!canBeScheduled(file)) return

    debouncer.queue(file) {
      dispatchRequest(file, reason)
    }

  /**
   * Dispatches a compilation request immediately and cancels any pending scheduled requests for the file.
   *
   * Use for low-frequency events, such as tab switches or explicit user actions, that require
   * an immediate decision. These events bypass the debounce window because they are unlikely
   * to be immediately followed by newer, invalidating events.
   */
  def bypass(file: VirtualFile, reason: String): Unit =
    // Try to cancel the pending scheduled trigger so we don't dispatch twice.
    debouncer.cancel(file)
    dispatchRequest(file, reason)


  private def dispatchRequest(vf: VirtualFile, reason: String): Unit = executeOnBackgroundThreadInNotDisposed(project) {
    val requestId = TriggerPhaseEvents.newRequestId()
    tracer.instant(HighlightingTriggerPhaseEvent(requestId, reason))

    if (!canBeScheduled(vf)) {
      tracer.endTrace(requestId, "not eligible for compilation (pre-checks failed)")
      return
    }

    inReadAction {
      val psi = PsiManager.getInstance(project).findFile(vf)
      val doc = FileDocumentManager.getInstance().getDocument(vf)

      if canHighlight(vf, psi, doc)
      then
        val decision = logic.decide(vf)
        if servedFromStore(decision, psi) then
          // The recorded diagnostics still describe the file, so they are displayed rather than recomputed.
          val served = highlighters.renderRecordedDiagnostics(Set(vf))
          tracer.instant(RecordedDiagnosticsServedEvent(requestId, vf.getName, served))
          tracer.endTrace(requestId, s"$decision: rendered the recorded diagnostics")
          None
        else
          val request = CompilationRequest.fromDecision(
            project, vf, psi, doc, reason, requestId
          )(decision)
          // Tracing for when the decision resolves but the factory refuses to build a request
          if (request.isEmpty) {
            tracer.endTrace(requestId, "no compilation scheduled for this trigger")
          }
          request
      else
        // Tracing for when the PSI/Document/HighlightingEnabledFor checks fail
        tracer.endTrace(requestId, "not eligible for compilation (PSI/Document checks failed)")
        None
    }.foreach(service.requestCompilation)
  }


  /**
   * Whether the decision is answered out of the recorded diagnostics instead of by compiling.
   *
   * A worksheet is excluded from [[CompilationDecision.Recorded]]. Its highlightings are a by-product of
   * evaluating it, and the request that evaluates it is the only thing that produces its output, so serving
   * the previous diagnostics would leave it unevaluated. [[CompilationDecision.UpToDate]] serves them for a
   * worksheet as it always has: there the target is current, so there is nothing to re-evaluate against.
   */
  private def servedFromStore(decision: CompilationDecision, psi: PsiFile): Boolean = decision match
    case CompilationDecision.UpToDate => !isWorksheet(psi)
    case CompilationDecision.Recorded => !isWorksheet(psi)
    case _ => false

  private def isWorksheet(psi: PsiFile): Boolean = psi match
    case scalaFile: ScalaFile => scalaFile.isWorksheetFile
    case _ => false

  /**
   * Cheap checks that need no read action
   */
  private def canBeScheduled(file: VirtualFile): Boolean =
    !project.isDisposed &&
      file.isValid &&
      !file.isInstanceOf[VirtualFileWindow] &&
      TriggerUtil.isHighlightingEnabled &&
      ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)

  /**
   *  Read action needed
   */
  private def canHighlight(virtualFile: VirtualFile, psi: PsiFile, doc: Document) =
    (psi ne null) && (doc ne null) && TriggerUtil.isHighlightingEnabledFor(psi, virtualFile, project)

object HighlightingTriggerService:
  def apply(project: Project): HighlightingTriggerService = project.getService(classOf[HighlightingTriggerService])
