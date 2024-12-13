package org.jetbrains.plugins.scala.compiler.references.indices

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex
import org.jetbrains.plugins.scala.compiler.references.indexDir

private[references] object ScalaCompilerReferenceReaderFactory {
  val expectedIndexVersion: Int = ScalaCompilerIndices.version

  val Log: Logger = Logger.getInstance(classOf[ScalaCompilerReferenceReaderFactory.type])

  //noinspection UnstableApiUsage
  def apply(project: Project): Option[ScalaCompilerReferenceReader] = {
    try {
      for {
        dir <- indexDir(project)
        if CompilerReferenceIndex.exists(dir) &&
          !CompilerReferenceIndex.versionDiffers(dir, expectedIndexVersion)
      } yield new ScalaCompilerReferenceReader(dir)
    } catch {
      case e: Exception =>
        indexDir(project).foreach(CompilerReferenceIndex.removeIndexFiles)
        Log.error(e)
        None
    }
  }
}
