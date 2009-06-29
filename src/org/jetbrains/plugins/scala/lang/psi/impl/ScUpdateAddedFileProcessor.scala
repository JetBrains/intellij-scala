package org.jetbrains.plugins.scala.lang.psi.impl


import api.ScalaFile
import api.toplevel.packaging.ScPackageStatement
import com.intellij.psi.impl.file.UpdateAddedFileProcessor
import com.intellij.psi.{PsiPackage, PsiDirectory, JavaDirectoryService, PsiFile}
/**
 * User: Alexander Podkhalyuzin
 * Date: 29.06.2009
 */

class ScUpdateAddedFileProcessor extends UpdateAddedFileProcessor {
  def update(element: PsiFile, originalElement: PsiFile): Unit = {
    if (!element.isInstanceOf[ScalaFile]) return
    //replace package name
    val file: ScalaFile = element.asInstanceOf[ScalaFile]
    val dir: PsiDirectory = file.getContainingDirectory
    if (dir == null) return;
    val aPackage: PsiPackage = JavaDirectoryService.getInstance().getPackage(dir)
    if (file.getPackageName != aPackage.getQualifiedName) {
      file.setPackageName(aPackage.getQualifiedName)
    }
  }

  def canProcessElement(element: PsiFile): Boolean = element.isInstanceOf[ScalaFile]
}