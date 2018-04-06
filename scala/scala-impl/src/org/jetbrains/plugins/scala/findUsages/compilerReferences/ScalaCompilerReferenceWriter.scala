package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.backwardRefs.CompilerReferenceWriter
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex

private class ScalaCompilerReferenceWriter protected (
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
  def apply(indexDir: File, isRebuild: Boolean): Option[ScalaCompilerReferenceWriter] = {
    if (CompilerReferenceIndex.versionDiffers(indexDir, ScalaCompilerIndices.getIndices)) {
      CompilerReferenceIndex.removeIndexFiles(indexDir)
      
      if (isRebuild) Some(new ScalaCompilerReferenceWriter(new ScalaCompilerReferenceIndex(indexDir, readOnly = false)))
      else           None
    } else {
      if (isRebuild) CompilerReferenceIndex.removeIndexFiles(indexDir)
      Some(new ScalaCompilerReferenceWriter(new ScalaCompilerReferenceIndex(indexDir, readOnly = false)))
    }
  }
}
