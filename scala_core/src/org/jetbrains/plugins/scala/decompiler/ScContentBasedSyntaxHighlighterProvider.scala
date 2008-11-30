package org.jetbrains.plugins.scala.decompiler

import _root_.org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighter

import com.intellij.psi.impl.PsiManagerImpl

import java.io.{File, ByteArrayOutputStream}

import lang.psi.api.ScalaFile
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.{StdFileTypes, FileType, ContentBasedClassFileProcessor}
import com.intellij.openapi.fileTypes.{StdFileTypes, FileType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.compiled.ClsRepositoryPsiElement
import com.intellij.psi.PsiManager
import lang.psi.stubs.impl.ScFileStubImpl
import lang.psi.stubs.ScFileStub

/**
 * @author ilyas
 */

class ScContentBasedClassFileProcessor extends ContentBasedClassFileProcessor {

  def isApplicable(project: Project, vFile: VirtualFile): Boolean = {
    val ft = vFile.getFileType
    if (ft == StdFileTypes.CLASS) {
      PsiManager.getInstance(project).findFile(vFile) match {case scalaFile : ScalaFile => true case _ => false}
    } else false
  }

  def createHighlighter(projet: Project, file: VirtualFile) = new ScalaSyntaxHighlighter

  def obtainFileText(project: Project, file: VirtualFile): String = {
    val bytes = file.contentsToByteArray
    DecompilerUtil.decompile(bytes, file)._1
  }

  def obtainLanguageForFile(file: VirtualFile): Language = {
    if (DecompilerUtil.isScalaFile(file)) {
      ScalaFileType.SCALA_LANGUAGE
    } else null
  }

}