package org.jetbrains.plugins.scala.findUsages.compilerReferences.indices

import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex
import org.jetbrains.plugins.scala.findUsages.compilerReferences.bytecode.CompiledScalaFile

import java.io.File

private[findUsages] class ScalaCompilerReferenceIndex(
  buildDir: File,
  readOnly: Boolean
) extends CompilerReferenceIndex[CompiledScalaFile](
      ScalaCompilerIndices.getIndices,
      buildDir,
      readOnly,
      ScalaCompilerIndices.version
    )
