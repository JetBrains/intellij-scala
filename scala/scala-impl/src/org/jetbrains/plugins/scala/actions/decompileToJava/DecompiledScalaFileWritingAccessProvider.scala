package org.jetbrains.plugins.scala.actions.decompileToJava

import java.util
import java.util.Collections

import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem
import com.intellij.openapi.vfs.{VirtualFile, WritingAccessProvider}
import org.jetbrains.plugins.scala.extensions._

class DecompiledScalaFileWritingAccessProvider extends WritingAccessProvider {
  import DecompiledScalaFileWritingAccessProvider._

  override def isPotentiallyWritable(vfile: VirtualFile): Boolean = !isDecompiledFile(vfile)

  override def requestWriting(virtualFiles: VirtualFile*): util.Collection[VirtualFile] =
    Collections.emptyList()
}

object DecompiledScalaFileWritingAccessProvider {
  def isDecompiledFile(vfile: VirtualFile): Boolean =
    vfile.getFileSystem match {
      case _: DummyFileSystem =>
        val parent = vfile.getParent.toOption.map(_.getName)
        parent.contains(ScalaBytecodeDecompileTask.scalaDecompiledFolder)
      case _ => false
    }
}
