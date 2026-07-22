package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.QueueWaitOutcome
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.QueueWaitPhaseEvent.withOutcome
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.CompilationRequestsScheduler.ExecutionFunction
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.*
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.compiler.tracing.core.TracingOps
import org.jetbrains.plugins.scala.compiler.tracing.core.events.ContextTraceEvent

import scala.concurrent.duration.Deadline

/**
 * Request deduplication / merge logic, each rule is a [[PartialFunction]] defined only for one request
 * type.
 *
 * A rule decides whether to merge-and-compile the request, re-enqueue it with a later deadline, or drop it,
 * using the `compile` function it is given (see `CompilerHighlightingService.compile`). Any request type
 * without a dedicated rule is simply compiled as-is.
 */
object DeduplicationLogic {

  /** A rule, defined only for the request type it deduplicates. */
  private type Rule = PartialFunction[CompilationRequest, Unit]

  private val Log = Logger.getInstance(getClass)

  def apply(project: Project,
            queue: PriorityQueue[CompilationRequest],
            compile: CompilationRequest => Unit): ExecutionFunction =
      incremental(project, queue, compile) orElse
      document(project, queue, compile) orElse
      worksheet(project, queue, compile) orElse
      fallback(project, compile)

  /** Total catch-all: no dedup rule matched this request type, so just run it. */
  private def fallback(project: Project, compile: CompilationRequest => Unit): Rule = {
    case request =>
      Log.warn(s"No deduplication rule matched request $request; executing it without merging")
      Tracing(project).mapAndEnd(request.id)(withOutcome(QueueWaitOutcome.Executed))
      compile(request)
  }

  private def incremental(project: Project,
                          queue: PriorityQueue[CompilationRequest],
                          compile: CompilationRequest => Unit): Rule = {
    case request: IncrementalRequest =>
      val tracer = Tracing(project)

      // Gather all other document and incremental compilation requests.
      val otherRequests = queue.iteratorFrom(request).collect {
        case dr: DocumentRequest => dr
        case ir: IncrementalRequest => ir
      }.toSeq

      // Merge only those requests which have a file open in any visible editor.
      val openFiles = FileEditorManager.getInstance(project).getSelectedFiles
      // Find all other requests that need to be merged with the current one.
      val toMerge = otherRequests.filter(shouldMerge(openFiles))
      // All other requests are removed from the queue.
      removeFromTheQueue(otherRequests, queue, req => {
        // Those with an open file are folded into this compilation; the rest are simply superseded by it.
        val outcome = if (toMerge.contains(req)) QueueWaitOutcome.Merged else QueueWaitOutcome.Superseded
        tracer.mapAndEnd(req.id)(withOutcome(outcome))
      })

      // Merge the compilation scopes. The logic here is the following.
      // Worksheet requests are something separate and not taken into account (technically, they are not
      // even present in the toMerge list, but needed for exhaustivity.
      // Incremental requests are broken down into their individual files that need to be highlighted which
      // are already open in an editor. Only those files are added to the merged compilation scope.
      // Same for document requests, except they only contain one file and have already been filtered before
      // in `shouldMerge` and are just included here as is.
      // We do not need to do additional module deduplication or filtering. We're essentially compiling
      // all leaf modules necessary to show error-highlighting information for the currently opened
      // files visible to the user. The JPS incremental compilation algorithm will take care of computing
      // all necessary module dependencies which also need to be compiled to achieve that.
      val initialScopes = request.fileCompilationScopes.filter { case (vf, _) => openFiles.contains(vf) }
      val mergedScopes = toMerge.foldLeft(initialScopes) {
        case (acc, ir: IncrementalRequest) => acc ++ ir.fileCompilationScopes.filter { case (vf, _) => openFiles.contains(vf) }
        case (acc, dr: DocumentRequest) => acc + (dr.scope.virtualFile -> dr.scope)
        case (acc, _) => acc
      }

      // It might happen that the merged scope has nothing to compile, for example, if there are no files
      // open for editing. This is fine, as the project will be incrementally compiled the next time a file
      // is opened. If the merged scope is empty (no modules to compile), we simply drop the request.
      if (mergedScopes.nonEmpty) {
        tracer.mapAndEnd(request.id)(withOutcome(QueueWaitOutcome.Executed))
        compile(request.withScopes(mergedScopes))
      } else {
        // The merged scope had nothing to compile, so the request is dropped.
        tracer.mapAndEnd(request.id)(withOutcome(QueueWaitOutcome.Dropped))
      }
  }

  private def document(project: Project,
                       queue: PriorityQueue[CompilationRequest],
                       compile: CompilationRequest => Unit): Rule = {
    case request: DocumentRequest =>
      val tracer = Tracing(project)
      // Document requests for the same file are debounced and deduplicated from the queue.
      val others = queue.iteratorFrom(request).collect {
        case dr: DocumentRequest if dr.scope.virtualFile == request.scope.virtualFile => dr
      }.toSeq
      val newDeadline: Deadline = getLatestDeadline(request, others)
      // All document requests for the same file are removed from the queue.
      removeFromTheQueue(others, queue, req => tracer.mapAndEnd(req.id)(withOutcome(QueueWaitOutcome.Merged)))

      // Instantiate the new document request and execute it if ready, schedule it if not.
      val merged = request.delayed(newDeadline)
      compileIfReady(queue, request, tracer, merged, compile)
  }

  private def worksheet(project: Project,
                        queue: PriorityQueue[CompilationRequest],
                        compile: CompilationRequest => Unit): Rule = {
    case request: WorksheetRequest =>
      val tracer = Tracing(project)
      // Worksheet requests for the same file are debounced and deduplicated from the queue.
      val others = queue.iteratorFrom(request).collect {
        case wr: WorksheetRequest if wr.virtualFile == request.virtualFile => wr
      }.toSeq
      // If any of the requests have `isFirstTimeHighlighting = true`, the debounced request will also have the same value.
      val firstTime = request.isFirstTimeHighlighting || others.exists(_.isFirstTimeHighlighting)

      val newDeadline = getLatestDeadline(request, others)
      // All worksheet requests for the same file are removed from the queue.
      removeFromTheQueue(others, queue, req => tracer.mapAndEnd(req.id)(withOutcome(QueueWaitOutcome.Merged)))

      // Instantiate the new worksheet request and execute it if ready, schedule it if not.
      val merged = new WorksheetRequest(request.file, request.virtualFile, request.document, firstTime,
        request.debugReason, newDeadline, request.requestId, project)
      compileIfReady(queue, request, tracer, merged, compile)
  }


  private def removeFromTheQueue(others: Seq[CompilationRequest],
                                 queue: PriorityQueue[CompilationRequest],
                                 cb: CompilationRequest => Unit): Unit = {
    others.foreach { other =>
      cb(other)
      queue.removeElement(other)
    }
  }
  // Look for the request scheduled furthest in the future; its deadline becomes the new deadline.
  private def getLatestDeadline(request: CompilationRequest, others: Seq[CompilationRequest]) =
    (request.deadline +: others.map(_.deadline)).max

  private def compileIfReady(queue: PriorityQueue[CompilationRequest],
                             request: CompilationRequest,
                             tracer: TracingOps[ContextTraceEvent],
                             merged: CompilationRequest,
                             compile: CompilationRequest => Unit): Unit = {
    if (merged.isReadyForExecution == RequestState.Ready) {
      tracer.mapAndEnd(request.id)(withOutcome(QueueWaitOutcome.Executed))
      compile(merged)
    } else {
      tracer.carry(request.id, merged.id)
      queue.enqueue(merged)
    }
  }
  private def shouldMerge(openFiles: Array[VirtualFile])(request: CompilationRequest): Boolean = request match {
    case ir: IncrementalRequest => ir.fileCompilationScopes.keys.exists(openFiles.contains)
    case dr: DocumentRequest => openFiles.contains(dr.scope.virtualFile)
    case _ => false
  }
}
