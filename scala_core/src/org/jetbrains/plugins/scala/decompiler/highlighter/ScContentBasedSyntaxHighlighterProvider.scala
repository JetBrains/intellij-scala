package org.jetbrains.plugins.scala.decompiler.highlighter

import _root_.org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighter
import com.intellij.openapi.fileTypes.{StdFileTypes, ContentBasedSyntaxHighlighterProvider, FileType}
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

class ScContentBasedSyntaxHighlighterProvider extends ContentBasedSyntaxHighlighterProvider {

  def isApplicable(ft: FileType, project: Project, vFile: VirtualFile): Boolean = {
    if (ft == StdFileTypes.CLASS) {
      val manager = PsiManager.getInstance(project)
      val file = manager.findFile(vFile)
      if (file != null &&
          file.isInstanceOf[ClsRepositoryPsiElement[_]]) {
        val clsFile = file.asInstanceOf[ClsRepositoryPsiElement[_]]
        val stub = clsFile.getStub
        val value = stub != null && stub.isInstanceOf[ScFileStubImpl]
        println(value)
        return value
      }
    }
    false
  }

  def createHighlighter(ft: FileType, projet: Project, file: VirtualFile) = new ScalaSyntaxHighlighter

}