package org.jetbrains.plugins.scala.tasty

import com.intellij.openapi.fileTypes.BinaryFileDecompiler
import com.intellij.openapi.vfs.VirtualFile

class TastyDecompiler extends BinaryFileDecompiler {
  override def decompile(file: VirtualFile): String = {

    // TODO An option to inspect a file, not just class, https://github.com/lampepfl/dotty-feature-requests/issues/96
    locationOf(file) match {
      case Some(Location(outputDirectory, className)) =>
        TastyReader.readText(outputDirectory, className)
          .getOrElse(s"Error reading TASTy file: ${file.getPath}")

      case _ => s"Cannot determine output directory: ${file.getPath}"
    }
  }
}
