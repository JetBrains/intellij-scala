package org.jetbrains.plugins.scala.compiler.highlighting

import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.plugins.scala.util.CompilationId

case class FileCompilerGeneratedState(compilationId: CompilationId,
                                      highlightings: Set[ExternalHighlighting],
                                      types: Map[(PosInfo, PosInfo), String]) {

  def withExtraHighlightings(highlightings: Set[ExternalHighlighting]): FileCompilerGeneratedState =
    this.copy(highlightings = this.highlightings ++ highlightings)

  def withExtraTypes(types: Map[(PosInfo, PosInfo), String]): FileCompilerGeneratedState =
    this.copy(types = this.types ++ types)
}
