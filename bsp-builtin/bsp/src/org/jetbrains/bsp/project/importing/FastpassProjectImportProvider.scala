package org.jetbrains.bsp.project.importing

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nullable
import org.jetbrains.bsp.project.importing.setup.FastpassConfigSetup
import org.jetbrains.plugins.scala.extensions.PathExt

import scala.Iterator.iterate

object FastpassProjectImportProvider {
  def folderContainsPantsExec(virtualFile: VirtualFile): Boolean = {
    val pantsChild = virtualFile.findChild("pants")
    pantsChild != null && !pantsChild.isDirectory
  }

  def containsFastpassExecutable(virtualFile: VirtualFile): Boolean =
    Option(virtualFile.getFileSystem.getNioPath(virtualFile))
      .map(_.resolve(FastpassConfigSetup.fastpassRelativePath))
      .exists(f => f.exists && f.isRegularFile)

  def isFastpassCompatibleProjectRoot(virtualFile: VirtualFile): Boolean =
    folderContainsPantsExec(virtualFile) && containsFastpassExecutable(virtualFile)

  def pantsRoot(vFile: VirtualFile): Option[VirtualFile] =
    iterate(vFile)(_.getParent).takeWhile(_ != null).find(isFastpassCompatibleProjectRoot)

  private val logger = Logger.getInstance(classOf[FastpassProjectImportProvider.type])

  def canImport(@Nullable vFile: VirtualFile): Boolean = {
    if (vFile == null) return false

    try {
      pantsRoot(vFile).isDefined
    } catch {
      case e: Throwable =>
        logger.error(e)
        false
    }
  }
}
