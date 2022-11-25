package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import org.jetbrains.jps.incremental.scala.remote.SourceScope
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

private sealed trait CompilationRequest {
  val priority: Int
  val document: Document
  val debugReason: String
}

private object CompilationRequest {
  /**
   * @param isFirstTimeHighlighting whether worksheet editor has just been selected (is true every time when tabs are switched)
   */
  final case class WorksheetRequest(
    file: ScalaFile,
    document: Document,
    isFirstTimeHighlighting: Boolean,
    debugReason: String,
  )
    extends CompilationRequest {
    override val priority: Int = 0
  }

  final case class IncrementalRequest(
    module: Module,
    sourceScope: SourceScope,
    document: Document,
    psiFile: PsiFile,
    debugReason: String
  ) extends CompilationRequest {
    override val priority: Int = 1
  }

  final case class DocumentRequest(
    module: Module,
    sourceScope: SourceScope,
    document: Document,
    debugReason: String
  )
    extends CompilationRequest {
    override val priority: Int = 2
  }

  implicit val compilationRequestOrdering: Ordering[CompilationRequest] =
    Ordering.by(_.priority)
}
