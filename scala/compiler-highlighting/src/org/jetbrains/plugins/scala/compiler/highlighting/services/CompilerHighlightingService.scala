package org.jetbrains.plugins.scala.compiler.highlighting.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.runner.{HighlightingRunner, RequestInfo}
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.{RequestProvider, TracingProvider}
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.{CompilationRequest, HighlightingRequest}
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.compiler.tracing.core.events.BaseEvent

import scala.language.implicitConversions

final private class CompilerHighlightingService(project: Project) extends HighlightingService:

  private case class Provider() extends
    RequestProvider(new HighlightingPlanRuntime(project))
    with TracingProvider(project)
  private val provider: RequestProvider = Provider()

  private val progressService: ProjectProgressService = ProjectProgressService(project)

  private val Log = Logger.getInstance(classOf[CompilerHighlightingService])
  /** used to easily pass `compile` when creating the HighlightingRunner */
  private given Conversion[RequestInfo, CompilationRequest] = r => r._1
  
  private val runner = HighlightingRunner(provider, compile)
  
  override def request(reason: String): Unit =
    Tracing(project).instant(BaseEvent("Waking up runner", args = Map("Reason" -> reason)).closed())
    runner.wakeUp()
  
  override def request(request: HighlightingRequest): Unit =
    provider.add(request)
    this.request(request.debugReason)
  
  def compile(request: CompilationRequest): Unit = if !project.isDisposed 
    then request.execute()
    else Log.warn(s"project disposed, skipped: $request")

  override def dispose(): Unit =
    Disposer.dispose(runner)
    Disposer.dispose(provider)
    progressService.cancel()
