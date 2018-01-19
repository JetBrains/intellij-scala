package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.util

import com.intellij.util.indexing.{IndexExtension, IndexId}
import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.jps.backwardRefs.index.{CompiledFileData, JavaCompilerIndices}

private object ScalaCompilerIndices {
  val backwardUsages: IndexId[CompilerRef, Integer]                         = JavaCompilerIndices.BACK_USAGES
  val backwardHierarchy: IndexId[CompilerRef, util.Collection[CompilerRef]] = JavaCompilerIndices.BACK_HIERARCHY

  val getIndices: util.Collection[IndexExtension[_, _, CompiledFileData]] = util.Arrays.asList(
    JavaCompilerIndices.createBackwardUsagesExtension(),
    JavaCompilerIndices.createBackwardHierarchyExtension()
  )
}
