package org.jetbrains.plugins.scala.externalHighlighters

import org.jetbrains.plugins.scala.util.CompilationId

case class FileCompilerGeneratedState(compilationId: CompilationId,
                                      highlightings: Set[ExternalHighlighting])
