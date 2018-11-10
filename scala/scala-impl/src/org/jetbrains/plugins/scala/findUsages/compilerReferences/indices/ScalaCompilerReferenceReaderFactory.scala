package org.jetbrains.plugins.scala.findUsages.compilerReferences.indices

import com.intellij.openapi.project.Project
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex
import org.jetbrains.plugins.scala.findUsages.compilerReferences.indexDir

private[compilerReferences] object ScalaCompilerReferenceReaderFactory {
  val expectedIndexVersion: Int = ScalaCompilerIndices.version

  def apply(project: Project): Option[ScalaCompilerReferenceReader] =
    for {
      dir <- indexDir(project)
      if CompilerReferenceIndex.exists(dir) &&
        !CompilerReferenceIndex.versionDiffers(dir, expectedIndexVersion)
    } yield new ScalaCompilerReferenceReader(dir)
}
