package org.jetbrains.plugins.scala.findUsages.compilerReferences.indices

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.backwardRefs.CompilerReferenceWriter
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex
import org.jetbrains.plugins.scala.findUsages.compilerReferences.bytecode.CompiledScalaFile

import java.io.File

private[compilerReferences] class ScalaCompilerReferenceWriter protected (
  index: ScalaCompilerReferenceIndex
) extends CompilerReferenceWriter[CompiledScalaFile](index) {

  def close(shouldClearIndex: Boolean): Unit = {
    if (shouldClearIndex) FileUtil.delete(index.getIndicesDir)
    super.close()
  }

  def registerClassfileData(data: CompiledScalaFile): Unit = {
    val fileId = enumeratePath(data.file.getPath)
    writeData(fileId, data)
  }

  def processDeletedFile(path: String): Unit = {
    val fileId = enumeratePath(path)
    writeData(fileId, null)
  }

  def enumerateName(name: String): Int = index.getByteSeqEum.enumerate(name)
}

private object ScalaCompilerReferenceWriter {
  def apply(indexDir: File, expectedVersion: Int, isRebuild: Boolean): Option[ScalaCompilerReferenceWriter] = {
    if (CompilerReferenceIndex.versionDiffers(indexDir, expectedVersion)) {
      CompilerReferenceIndex.removeIndexFiles(indexDir)

      if (isRebuild) Some(new ScalaCompilerReferenceWriter(new ScalaCompilerReferenceIndex(indexDir, readOnly = false)))
      else           None
    } else Some(new ScalaCompilerReferenceWriter(new ScalaCompilerReferenceIndex(indexDir, readOnly = false)))
  }
}
