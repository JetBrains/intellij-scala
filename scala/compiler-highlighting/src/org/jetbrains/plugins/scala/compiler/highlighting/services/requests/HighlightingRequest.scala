package org.jetbrains.plugins.scala.compiler.highlighting.services.requests

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.RequestId

import java.util.concurrent.atomic.AtomicLong

trait HighlightingRequest:
  
  val sequence: Long
  
  /** Identifies the request across its lifecycle (queueing, merging, tracing). */
  val id: RequestId

  /** Human-readable reason the request was triggered, used for logging and tracing. */
  def debugReason: String
  
  
  /** The file on which the highlighting has been requested */
  val file: VirtualFile

  
object HighlightingRequest:

  given Ordering[HighlightingRequest] = Ordering.by[HighlightingRequest, Long](_.sequence).reverse
  
  private object RequestClock:
    private val sequence = new AtomicLong(0L)
    def nextTick(): Long = sequence.incrementAndGet()
  
  private case class HighlightingRequestImpl(override val id: RequestId,
                                             override val file: VirtualFile,
                                             override val debugReason: String,
                                             override val sequence: Long) extends HighlightingRequest
  
  def apply(id: RequestId, file: VirtualFile, debugReason: String): HighlightingRequest = 
    HighlightingRequestImpl(id, file, debugReason, RequestClock.nextTick())
  extension (req: HighlightingRequest)
    def newWithDebugReason(reason:String): HighlightingRequest = 
      HighlightingRequest(req.id, req.file, 
        if !req.debugReason.contains(reason)
          then s"${req.debugReason} $reason"
          else req.debugReason)
