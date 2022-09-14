package org.jetbrains.plugins.scala.compiler.references.indices

import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex
import org.jetbrains.plugins.scala.compiler.references.bytecode.CompiledScalaFile

import java.io.File

private[references] class ScalaCompilerReferenceIndex(
  buildDir: File,
  readOnly: Boolean
) extends CompilerReferenceIndex[CompiledScalaFile](
      ScalaCompilerIndices.getIndices,
      buildDir,
      readOnly,
      ScalaCompilerIndices.version
    )
