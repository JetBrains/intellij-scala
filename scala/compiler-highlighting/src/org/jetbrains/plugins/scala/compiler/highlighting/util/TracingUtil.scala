package org.jetbrains.plugins.scala.compiler.highlighting.util

import org.jetbrains.plugins.scala.compiler.tracing.core.TracingOps
import org.jetbrains.plugins.scala.compiler.tracing.core.events.{ContextTraceEvent, EndEvent}

object TracingUtil:
  
  extension(ops: TracingOps[ContextTraceEvent])
    def endTrace(id: Any, reason: String = ""):Unit = ops.instant(EndEvent(id, reason))
      
      
