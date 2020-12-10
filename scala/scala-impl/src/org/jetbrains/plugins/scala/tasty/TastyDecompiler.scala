package org.jetbrains.plugins.scala.tasty

import com.intellij.openapi.fileTypes.BinaryFileDecompiler
import com.intellij.openapi.vfs.VirtualFile

class TastyDecompiler extends BinaryFileDecompiler {
  override def decompile(file: VirtualFile): String = {
    // TODO An option to inspect a file, not just class, https://github.com/lampepfl/dotty-feature-requests/issues/96
    // TODO How can we show rightHandSide without causing "file/doc text length different" exception in FoldingUpdate.getFoldingsFor?
    TastyPath(file)
      .map(path => TastyReader.read(path, rightHandSide = false).map(_.text).getOrElse(s"Error reading TASTy file: ${file.getPath}"))
      .getOrElse(s"Cannot determine path of: ${file.getPath}")
  }
}
