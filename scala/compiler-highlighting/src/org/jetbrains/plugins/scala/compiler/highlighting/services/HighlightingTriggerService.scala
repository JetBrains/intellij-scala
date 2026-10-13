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
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.{HighlightingTriggerEvent, RecordedDiagnosticsServedEvent}
import org.jetbrains.plugins.scala.compiler.highlighting.services.BackgroundExecutorService.executeOnBackgroundThreadInNotDisposed
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.ScheduledDebouncer
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.HighlightingRequest
import org.jetbrains.plugins.scala.compiler.highlighting.triggers.TriggerUtil
import org.jetbrains.plugins.scala.compiler.highlighting.util.TracingUtil.endTrace
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode

import scala.concurrent.duration.FiniteDuration

/**
 * Where every trigger arrives, and the only thing any of them does.
 *
 * A trigger names a file and says why. It does not decide what that file needs: that is settled later,
 * when the scheduler asks, against the state as it is then. So this is deliberately cheap, and no trigger
 * is more meaningful than another.
 */
@Service(Array(Service.Level.PROJECT))
private final class HighlightingTriggerService(project: Project) extends TriggerService:

  /**
   * The quiet period a file needs before its trigger is handed over.
   *
   * This is the user's compiler highlighting delay, because this is the timer that says:
   * how long after the last edit to react.
   *
   */
  private[highlighting] def debounceDelay: FiniteDuration = ScalaHighlightingMode.compilationDelay(project)

  private val debouncer = new ScheduledDebouncer[VirtualFile](
    debounceDelay,
    AppExecutorUtil.createBoundedScheduledExecutorService("HighlightingTriggerDebouncer", 1),
    project
  )
  private val logic = CompilationLogicService(project)
  private val tracer = Tracing(project)
  private val Log = Logger.getInstance(classOf[HighlightingTriggerService])
  private val highlighters = ExternalHighlightersService(project)
  
  def schedule(@Nullable file: VirtualFile, reason: String): Unit =
    if file eq null then return  // end tracing here if needed
    if canBeScheduled(file) then debouncer.queue(file)(handOver(file, reason))
  
  def bypass(@Nullable file: VirtualFile, reason: String): Unit =
    if file eq null then return // end tracing here if needed
    debouncer.cancel(file)
    if canBeScheduled(file) then handOver(file, reason)

  // [services] just for testing purposes
  private[services] def processTrigger(file: VirtualFile, reason: String): Unit = 
    val requestId = TriggerPhaseEvents.newRequestId()
    tracer.instant(HighlightingTriggerEvent(requestId, reason))

    if (!canBeScheduled(file)) {
      tracer.endTrace(requestId, "not eligible for compilation (pre-checks failed)")
    } else {
      requestHighlighting(file, reason, requestId)
    }
  
  private def requestHighlighting(file: VirtualFile, reason: String, requestId: TriggerPhaseEvents.RequestId): Unit =
    inReadAction:
      val psi = PsiManager.getInstance(project).findFile(file)
      val doc = FileDocumentManager.getInstance().getDocument(file)

      if canHighlight(file, psi, doc) then
        Log.debug(s"decision lookup for: $reason")
        val decision = logic.decide(file)
        if decision.servedFromStore(psi) then
          tracer.instant(RecordedDiagnosticsServedEvent(requestId, file.getPresentableName))
          val served = highlighters.renderRecordedDiagnostics(Set(file), requestId)
          None
        else
          Some(HighlightingRequest(requestId, file, reason))
      else
        tracer.endTrace(requestId, "not eligible for compilation (PSI/Document checks failed)")
        None
    .foreach(HighlightingService(project).request)

  private def handOver(file: VirtualFile, reason: String): Unit = executeOnBackgroundThreadInNotDisposed(project):
      processTrigger(file, reason)
      
  /**
   * Cheap checks that need no read action
   */
  private def canBeScheduled(file: VirtualFile): Boolean =
    !project.isDisposed &&
      TriggerUtil.isHighlightingEnabled(project) &&
      file.isValid &&
      !file.isInstanceOf[VirtualFileWindow]

  /**
   *  Read action needed
   */
  private def canHighlight(virtualFile: VirtualFile, psi: PsiFile, doc: Document) =
    (psi ne null) && (doc ne null) && TriggerUtil.isHighlightingEnabledFor(psi, virtualFile, project)

