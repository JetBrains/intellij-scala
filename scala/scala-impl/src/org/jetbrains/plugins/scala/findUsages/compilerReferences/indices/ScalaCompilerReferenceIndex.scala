package org.jetbrains.plugins.scala.findUsages.compilerReferences.indices

import java.io.File

import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex
import org.jetbrains.plugins.scala.findUsages.compilerReferences.bytecode.CompiledScalaFile

private[findUsages] class ScalaCompilerReferenceIndex(
  buildDir: File,
  readOnly: Boolean
) extends CompilerReferenceIndex[CompiledScalaFile](
      ScalaCompilerIndices.getIndices,
      buildDir,
      readOnly,
      ScalaCompilerIndices.version
    )
