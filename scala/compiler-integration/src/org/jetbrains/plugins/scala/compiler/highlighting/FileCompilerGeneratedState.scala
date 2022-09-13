package org.jetbrains.plugins.scala.compiler.highlighting

import org.jetbrains.plugins.scala.util.CompilationId

case class FileCompilerGeneratedState(compilationId: CompilationId,
                                      highlightings: Set[ExternalHighlighting]) {

  def withExtraHighlightings(highlightings: Set[ExternalHighlighting]): FileCompilerGeneratedState =
    this.copy(highlightings = this.highlightings ++ highlightings)
}
