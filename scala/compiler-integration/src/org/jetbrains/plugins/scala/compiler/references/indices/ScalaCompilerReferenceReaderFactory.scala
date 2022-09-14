package org.jetbrains.plugins.scala.compiler.references.indices

import com.intellij.openapi.project.Project
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex
import org.jetbrains.plugins.scala.compiler.references.indexDir

private[references] object ScalaCompilerReferenceReaderFactory {
  val expectedIndexVersion: Int = ScalaCompilerIndices.version

  def apply(project: Project): Option[ScalaCompilerReferenceReader] =
    for {
      dir <- indexDir(project)
      if CompilerReferenceIndex.exists(dir) &&
        !CompilerReferenceIndex.versionDiffers(dir, expectedIndexVersion)
    } yield new ScalaCompilerReferenceReader(dir)
}
