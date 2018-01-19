package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.backwardRefs.CompilerReferenceWriter
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex

private class ScalaCompilerReferenceWriter protected (
  index: ScalaCompilerReferenceIndex
) extends CompilerReferenceWriter[ClassfileData](index) {

  def close(shouldClearIndex: Boolean): Unit = {
    if (shouldClearIndex) FileUtil.delete(index.getIndicesDir)
    super.close()
  }

  def registerClassfileData(data: ClassfileData): Unit = {
    val fileId = enumeratePath(data.sourceFile.getPath)
    writeData(fileId, data)
  }

  def processDeletedFile(path: String): Unit = {
    val fileId = enumeratePath(path)
    writeData(fileId, null)
  }

  def enumerateName(name: String): Int = index.getByteSeqEum.enumerate(name)
}

private object ScalaCompilerReferenceWriter {
  def apply(indexDir: File, affectedModules: Set[String], isRebuild: Boolean): Option[ScalaCompilerReferenceWriter] = {
    if (CompilerReferenceIndex.versionDiffers(indexDir, ScalaCompilerIndices.getIndices)) {
      CompilerReferenceIndex.removeIndexFiles(indexDir)
      // @TODO:
      // Suggest project rebuild if index version changed and all module are affected
      // and disable indices otherwise.
      //
      if (isRebuild) Some(new ScalaCompilerReferenceWriter(new ScalaCompilerReferenceIndex(indexDir, readOnly = false)))
      else None
    } else {
      Some(new ScalaCompilerReferenceWriter(new ScalaCompilerReferenceIndex(indexDir, readOnly = false)))
    }
  }
}
