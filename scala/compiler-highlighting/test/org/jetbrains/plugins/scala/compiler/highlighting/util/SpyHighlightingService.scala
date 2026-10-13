package org.jetbrains.plugins.scala.compiler.highlighting.util

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.highlighting.services.HighlightingService
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.{CompilationRequest, HighlightingRequest}

class SpyHighlightingService(project: Project)
  extends HighlightingService:

  var dispatchedRequests: List[HighlightingRequest] = List.empty
  var parameterlessRequestCount: Int = 0

  override def request(request: HighlightingRequest): Unit =
    dispatchedRequests :+= request

  override def request(reason: String): Unit =
    parameterlessRequestCount += 1

  override def compile(request: CompilationRequest): Unit = ()
  override def dispose(): Unit = ()

