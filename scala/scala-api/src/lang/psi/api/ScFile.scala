package org.jetbrains.plugins.scala
package lang.psi.api

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClassOwnerEx
import com.intellij.psi.impl.source.PsiFileWithStubSupport
import com.intellij.testFramework.LightVirtualFile

trait ScFile extends PsiFileWithStubSupport
  with PsiClassOwnerEx {

  def isCompiled: Boolean
}

object ScFile {

  implicit class ScFileExt(private val file: ScFile) extends AnyVal {

    def findVirtualFile: Option[VirtualFile] = VirtualFile.unapply(file)
  }

  object VirtualFile {

    def unapply(file: ScFile): Option[VirtualFile] = {
      val originalFile = file.getOriginalFile
      originalFile.getVirtualFile match {
        case null =>
          file.getViewProvider.getVirtualFile match {
            case vFile: LightVirtualFile => Option(vFile.getOriginalFile)
            case _                       => None
          }
        case virtualFile =>
          Some(virtualFile)
      }
    }
  }

}
