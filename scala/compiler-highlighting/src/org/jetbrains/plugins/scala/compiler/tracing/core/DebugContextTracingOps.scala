package org.jetbrains.plugins.scala.compiler.tracing.core

import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.compiler.tracing.core.events.{BaseEvent, ContextTraceEvent}

import java.util.concurrent.{ScheduledFuture, TimeUnit}

class DebugContextTracingOps[S <: TraceSpan[ContextTraceEvent]] (
  service: TracerService[ContextTraceEvent, S],
  lifecycleRegistry: Registry[Any, TraceSpan[ContextTraceEvent]]
) extends ContextTracingOps[S](
  service,
  lifecycleRegistry
) with TracingOps[ContextTraceEvent]{

  private val log = Logger.getInstance(classOf[ContextTracingOps[?]])

  private case class EndDebug(evt: ContextTraceEvent) extends
    BaseEvent(evt.name, evt.parentKey, evt.key, evt.closeParent, closeOnEnd = evt.closeOnEnd, evt.args ++ 
      Map("parentKey" -> evt.parentKey.toString,
          "key" -> evt.key.toString,
          "closed" -> (evt.closeOnEnd || evt.key.isEmpty).toString,
          "closeParent" -> evt.closeParent.toString
      )
    )
  
  override def end(span: TraceSpan[ContextTraceEvent]): Unit =
    super.end(span.withEvent(EndDebug(span.event)))

  private val scheduledLogger: ScheduledFuture[?] =
    JobScheduler.getScheduler.scheduleWithFixedDelay(() => {
      log.warn(s"[Leak Detector] ContextRegistry state: $contextRegistry")
    }, 2, 2, TimeUnit.SECONDS)

  def close(): Unit = {
    scheduledLogger.cancel(false)
  }
}
