package org.jetbrains.bsp.project.importing

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.bsp.project.importing.setup.FastpassConfigSetup
import scala.Iterator.iterate

object FastpassProjectImportProvider {
  def folderContainsPantsExec(virtualFile: VirtualFile): Boolean = {
    val pantsChild = virtualFile.findChild("pants")
    pantsChild != null && !pantsChild.isDirectory
  }

  def containsFastpassExecutable(virtualFile: VirtualFile): Boolean = {
    val fastpassFile = virtualFile.toNioPath.resolve(FastpassConfigSetup.fastpassRelativePath).toFile
    fastpassFile.exists && fastpassFile.isFile
  }

  def isFastpassCompatibleProjectRoot(virtualFile: VirtualFile): Boolean =
    folderContainsPantsExec(virtualFile) && containsFastpassExecutable(virtualFile)

  def pantsRoot(vFile: VirtualFile): Option[VirtualFile] =
    iterate(vFile)(_.getParent).takeWhile(_ != null).find(isFastpassCompatibleProjectRoot)

  def canImport(vFile: VirtualFile): Boolean =
    pantsRoot(vFile).isDefined
}
