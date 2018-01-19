package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.util

import com.intellij.compiler.backwardRefs.CompilerReferenceReaderFactory
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.IndexExtension
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.jps.backwardRefs.index.{CompiledFileData, CompilerReferenceIndex}

private object ScalaCompilerReferenceReaderFactory
    extends CompilerReferenceReaderFactory[ScalaCompilerReferenceReader] {
  private val logger = Logger.getInstance(ScalaCompilerReferenceReaderFactory.getClass)

  @NotNull
  override def getIndices: util.Collection[_ <: IndexExtension[_, _, _ <: CompiledFileData]] =
    ScalaCompilerIndices.getIndices

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
        !CompilerReferenceIndex.versionDiffers(dir, getIndices)
    } yield new ScalaCompilerReferenceReader(dir)
}
