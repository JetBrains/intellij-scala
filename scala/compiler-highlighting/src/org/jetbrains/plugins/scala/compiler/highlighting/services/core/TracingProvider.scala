package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.HighlightingRequestEvent.withOutcome
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.QueueWaitOutcome.{Dropped, Executed, Superseded}
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.{CompilationRequestEvent, HighlightingRequestEvent}
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.runner.RequestInfo
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.HighlightingRequest
import org.jetbrains.plugins.scala.compiler.tracing.Tracing

trait TracingProvider(project: Project) extends RequestProvider:

  override protected def removeExecutedRequests(requestInfo: RequestInfo): RequestInfo =
    val candidateReq = requestInfo._2.maxBy(_.sequence)
    Tracing(project).mapAndEnd(candidateReq.id)(withOutcome(Executed))
    Tracing(project).instant(CompilationRequestEvent(candidateReq.id, requestInfo._1, "Compilation request created"))

    super.removeExecutedRequests(requestInfo)

  override def add(request: HighlightingRequest): Unit =
    held.get().get(request.file).foreach { oldReq =>
      Tracing(project).mapAndEnd(oldReq.id)(withOutcome(Superseded, "Newer request received for this file"))
    }

    Tracing(project).begin(request.id, HighlightingRequestEvent(request))
    super.add(request)


  override protected def take(covers: Iterable[HighlightingRequest], reason: String = ""): Seq[HighlightingRequest] =
    val requests = super.take(covers)
    requests.foreach(req => Tracing(project).mapAndEnd(req.id)(withOutcome(Dropped, reason)))
    requests