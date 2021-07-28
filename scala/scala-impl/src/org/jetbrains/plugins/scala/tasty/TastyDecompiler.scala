package org.jetbrains.plugins.scala.tasty

import com.intellij.openapi.fileTypes.BinaryFileDecompiler
import com.intellij.openapi.vfs.VirtualFile

class TastyDecompiler extends BinaryFileDecompiler {
  override def decompile(file: VirtualFile): String =
    TastyReader.read(file.contentsToByteArray())
      .map(_._2)
      .getOrElse(throw new RuntimeException(s"Error reading TASTy file: ${file.getPath}"))
}
