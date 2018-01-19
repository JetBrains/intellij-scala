package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.File

import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex

private[findUsages] class ScalaCompilerReferenceIndex(
  buildDir: File,
  readOnly: Boolean
) extends CompilerReferenceIndex[ClassfileData](ScalaCompilerIndices.getIndices, buildDir, readOnly)
