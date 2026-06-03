package org.jetbrains.plugins.scala.compiler.references.indices

import com.intellij.openapi.util.io.NioFiles
import org.jetbrains.jps.backwardRefs.CompilerReferenceWriter
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex
import org.jetbrains.plugins.scala.compiler.references.bytecode.CompiledScalaFile
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

private[references] class ScalaCompilerReferenceWriter protected (
  index: ScalaCompilerReferenceIndex
) extends CompilerReferenceWriter[CompiledScalaFile](index) {

  def close(shouldClearIndex: Boolean): Unit = {
    if (shouldClearIndex) NioFiles.deleteRecursively(index.getIndexDir)
    super.close()
  }

  def registerClassfileData(data: CompiledScalaFile): Unit = {
    val fileId = enumeratePath(data.file.toCanonicalPath.toString)
    writeData(fileId, data)
  }

  def processDeletedFile(path: String): Unit = {
    val fileId = enumeratePath(path)
    writeData(fileId, null)
  }

  def enumerateName(name: String): Int = index.getByteSeqEum.enumerate(name)
}

private object ScalaCompilerReferenceWriter {
  def apply(indexDir: Path, expectedVersion: Int, isRebuild: Boolean): Option[ScalaCompilerReferenceWriter] = {
    if (CompilerReferenceIndex.versionDiffers(indexDir, expectedVersion)) {
      CompilerReferenceIndex.removeIndexFiles(indexDir)

      if (isRebuild) Some(new ScalaCompilerReferenceWriter(new ScalaCompilerReferenceIndex(indexDir, readOnly = false)))
      else           None
    } else Some(new ScalaCompilerReferenceWriter(new ScalaCompilerReferenceIndex(indexDir, readOnly = false)))
  }
}
