package org.jetbrains.plugins.scala
package lang
package psi
package api

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.source.PsiFileWithStubSupport
import com.intellij.psi.{PsiClassOwnerEx, PsiImportHolder}
import com.intellij.testFramework.LightVirtualFile

trait ScFile extends PsiFileWithStubSupport
  with PsiClassOwnerEx
  with PsiImportHolder {

  private[scala] def virtualFile: VirtualFile

  private[scala] def virtualFile_=(virtualFile: VirtualFile): Unit

  def sourceName: String

  private[scala] def sourceName_=(sourceName: String): Unit
}

object ScFile {

  implicit class ScFileExt(private val file: ScFile) extends AnyVal {

    def isCompiled: Boolean = file.virtualFile != null

    def findVirtualFile: Option[VirtualFile] = VirtualFile.unapply(file)
  }

  object VirtualFile {

    def unapply(file: ScFile): Option[VirtualFile] = file.getVirtualFile match {
      case null =>
        file.getViewProvider.getVirtualFile match {
          case virtualFile: LightVirtualFile => Option(virtualFile.getOriginalFile)
          case _ => None
        }
      case virtualFile => Some(virtualFile)
    }
  }

}
