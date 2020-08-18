package org.jetbrains.sbt.project

import com.intellij.openapi.vfs.VirtualFile
import Iterator.iterate

object FastpassProjectImportProvider {
  def pantsRoot(vFile: VirtualFile): Option[VirtualFile] =
    iterate(vFile)(_.getParent).takeWhile(_ != null).find(_.findChild("pants") != null)

  def canImport(vFile: VirtualFile): Boolean =
    pantsRoot(vFile).isDefined
}
