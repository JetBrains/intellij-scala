package org.jetbrains.plugins.scala.compiler.highlighting.services.core.runner

import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.Provider
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.{CompilationRequest, HighlightingRequest}

import java.util.concurrent.ScheduledExecutorService
import scala.concurrent.duration.{DurationInt, FiniteDuration}


type RequestInfo = (CompilationRequest, Seq[HighlightingRequest])

/** Think about removing [[DebouncedRunner]] and using [[ExecutorRunner]] instead */
class HighlightingRunner(
  provider: Provider[HighlightingRequest, RequestInfo],
  execution: RequestInfo => Unit,
  debounceDelay: FiniteDuration = 300.milliseconds,
  executor: ScheduledExecutorService = AppExecutorUtil.createBoundedScheduledExecutorService("CompilerHighlightingScheduler", 1)
) extends 
  TaskRunner, 
  DebouncedRunner(executor, debounceDelay), 
  ProviderRunner[RequestInfo](provider, execution),
  DisposableRunner