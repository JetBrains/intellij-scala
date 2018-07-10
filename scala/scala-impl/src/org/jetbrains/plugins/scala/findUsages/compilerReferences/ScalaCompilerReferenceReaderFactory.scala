package org.jetbrains.plugins.scala.findUsages.compilerReferences

import com.intellij.compiler.backwardRefs.CompilerReferenceReaderFactory
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nullable
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex

private object ScalaCompilerReferenceReaderFactory
    extends CompilerReferenceReaderFactory[ScalaCompilerReferenceReader] {
  private val logger = Logger.getInstance(ScalaCompilerReferenceReaderFactory.getClass)

  override def expectedIndexVersion(): Int = ScalaCompilerIndices.version

  @Nullable
  override def create(project: Project): ScalaCompilerReferenceReader =
    try safeCreate(project).orNull
    catch {
      case e: RuntimeException =>
        logger.error("An exception occured while trying to initialize compiler reference index reader.", e)
        null
    }

  private def safeCreate(project: Project): Option[ScalaCompilerReferenceReader] =
    for {
      dir <- indexDir(project)
      if CompilerReferenceIndex.exists(dir) &&
        !CompilerReferenceIndex.versionDiffers(dir, expectedIndexVersion())
    } yield new ScalaCompilerReferenceReader(dir)
}
