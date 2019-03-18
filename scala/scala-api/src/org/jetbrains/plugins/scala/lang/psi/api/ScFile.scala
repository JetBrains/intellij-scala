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

  def isCompiled: Boolean
}

object ScFile {

  implicit class ScFileExt(private val file: ScFile) extends AnyVal {

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
