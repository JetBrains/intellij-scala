package org.jetbrains.plugins.scala
package lang
package psi
package api

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.source.PsiFileWithStubSupport
import com.intellij.psi.{PsiClassOwnerEx, PsiImportHolder}

trait ScFile extends PsiFileWithStubSupport
  with PsiClassOwnerEx
  with PsiImportHolder {

  private[scala] def virtualFile: VirtualFile

  private[scala] def virtualFile_=(virtualFile: VirtualFile): Unit
}

object ScFile {

  implicit class ScFileExt(private val file: ScFile) extends AnyVal {

    def isCompiled: Boolean = file.virtualFile != null
  }

}
