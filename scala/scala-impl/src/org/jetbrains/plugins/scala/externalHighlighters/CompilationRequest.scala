package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import org.jetbrains.jps.incremental.scala.remote.SourceScope
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

private sealed trait CompilationRequest {
  val priority: Int
  val document: Document
  val debugReason: String
}

private object CompilationRequest {
  final case class WorksheetRequest(file: ScalaFile, document: Document, debugReason: String)
    extends CompilationRequest {
    override val priority: Int = 0
  }

  final case class IncrementalRequest(module: Module, sourceScope: SourceScope, document: Document, debugReason: String)
    extends CompilationRequest {
    override val priority: Int = 1
  }

  final case class DocumentRequest(document: Document, sourceScope: SourceScope, debugReason: String)
    extends CompilationRequest {
    override val priority: Int = 2
  }

  implicit val compilationRequestOrdering: Ordering[CompilationRequest] =
    Ordering.by(_.priority)
}
