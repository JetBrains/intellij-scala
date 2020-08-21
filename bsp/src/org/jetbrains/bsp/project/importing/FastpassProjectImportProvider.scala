package org.jetbrains.bsp.project.importing

import com.intellij.openapi.vfs.VirtualFile
import scala.Iterator.iterate

object FastpassProjectImportProvider {
  def pantsRoot(vFile: VirtualFile): Option[VirtualFile] =
    iterate(vFile)(_.getParent).takeWhile(_ != null).find(_.findChild("pants") != null)

  def canImport(vFile: VirtualFile): Boolean =
    pantsRoot(vFile).isDefined
}
