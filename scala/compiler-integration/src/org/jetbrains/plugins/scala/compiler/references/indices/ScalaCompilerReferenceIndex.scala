package org.jetbrains.plugins.scala.compiler.references.indices

import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex
import org.jetbrains.plugins.scala.compiler.references.bytecode.CompiledScalaFile

import java.nio.file.Path

private[references] class ScalaCompilerReferenceIndex(
  buildDir: Path,
  readOnly: Boolean
) extends CompilerReferenceIndex[CompiledScalaFile](
      ScalaCompilerIndices.getIndices,
      buildDir,
      readOnly,
      ScalaCompilerIndices.version
    )
