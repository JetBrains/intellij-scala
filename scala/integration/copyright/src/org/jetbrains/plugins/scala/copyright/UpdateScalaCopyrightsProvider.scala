package org.jetbrains.plugins.scala.copyright

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.maddyhome.idea.copyright.CopyrightProfile
import com.maddyhome.idea.copyright.psi.{UpdateCopyright, UpdateCopyrightsProvider, UpdateJavaFileCopyright}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * @author Alexander Podkhalyuzin
 */

class UpdateScalaCopyrightsProvider extends UpdateCopyrightsProvider {
  def createInstance(project: Project, module: Module, file: VirtualFile, base: FileType,
                     options: CopyrightProfile): UpdateCopyright = {
    new UpdateJavaFileCopyright(project, module, file, options) {
      override def accept: Boolean = getFile.isInstanceOf[ScalaFile]

      override def getPackageStatement: PsiElement = {
        val file = getFile.asInstanceOf[ScalaFile]
        if (file.isScriptFile) return file.getFirstChild
        val packs = file.packagings
        if (packs.isEmpty) return null
        packs.head
      }

      override def getImportsList: Array[PsiElement] = {
        val file = getFile.asInstanceOf[ScalaFile]

        val arr: Array[PsiElement] = file.importStatementsInHeader.toArray
        if (arr.length == 0) null else arr
      }
    }
  }
}