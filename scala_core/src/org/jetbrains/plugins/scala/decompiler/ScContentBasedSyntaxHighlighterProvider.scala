package org.jetbrains.plugins.scala.decompiler

import _root_.org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighter
import com.intellij.psi.impl.PsiManagerImpl
import lang.psi.impl.compiled.ScClsFileImpl
import com.intellij.lang.Language
//todo[8858] import com.intellij.openapi.fileTypes.{StdFileTypes, FileType, ContentBasedClassFileProcessor}
import com.intellij.openapi.fileTypes.{StdFileTypes, FileType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.compiled.ClsRepositoryPsiElement
import com.intellij.psi.PsiManager
import java.io.File
import lang.psi.stubs.impl.ScFileStubImpl
import lang.psi.stubs.ScFileStub

/**
 * @author ilyas
 */

class ScContentBasedClassFileProcessor /*todo[8858] extends ContentBasedClassFileProcessor */{

  def isApplicable(project: Project, vFile: VirtualFile): Boolean = {
    val ft = vFile.getFileType
    if (ft == StdFileTypes.CLASS) {
      val manager = PsiManager.getInstance(project)
      val file = manager.findFile(vFile)
      if (file != null &&
          file.isInstanceOf[ClsRepositoryPsiElement[_]]) {
        val clsFile = file.asInstanceOf[ClsRepositoryPsiElement[_]]
        val stub = clsFile.getStub
        return stub != null && stub.isInstanceOf[ScFileStubImpl]
      }
    }
    false
  }

  def createHighlighter(projet: Project, file: VirtualFile) = new ScalaSyntaxHighlighter

  def obtainFileText(project: Project, file: VirtualFile): String = {
    val manager = PsiManager.getInstance(project).asInstanceOf[PsiManagerImpl]
    val provider = new ScClassFileViewProvider(manager, file, true)
    ""
/*todo[8858]
    val psiFile = new ScClsFileImpl(manager, provider, true)
    val buffer = new StringBuffer
    psiFile.appendMirrorText(0, buffer)
    buffer.toString
*/
  }

  def obtainLanguageForFile(file: VirtualFile): Language = {
    if (DecompilerUtil.isScalaFile(file)) {
      ScalaFileType.SCALA_LANGUAGE
    } else null
  }

}