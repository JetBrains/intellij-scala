package org.jetbrains.plugins.scala
package decompiler

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
import lang.psi.stubs.ScFileStub
import lang.formatting.settings.ScalaCodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager

/**
 * @author ilyas
 */
class ScContentBasedClassFileProcessor extends ContentBasedClassFileProcessor {

  def isApplicable(project: Project, vFile: VirtualFile): Boolean = {
    val ft = vFile.getFileType
    if (ft == StdFileTypes.CLASS) {
      PsiManager.getInstance(project).findFile(vFile) match {
        case scalaFile : ScalaFile => true
        case _ => DecompilerUtil.isScalaFile(vFile)
      }
    } else false
  }

  def createHighlighter(project: Project, file: VirtualFile) = {
    val treatDocCommentAsBlockComment = CodeStyleSettingsManager.getSettings(project).getCustomSettings(classOf[ScalaCodeStyleSettings]).TREAT_DOC_COMMENT_AS_BLOCK_COMMENT;
    new ScalaSyntaxHighlighter(treatDocCommentAsBlockComment)
  }

  def obtainFileText(project: Project, file: VirtualFile): String = {
    val text = DecompilerUtil.decompile(file, file.contentsToByteArray).sourceText
    text.replace( "\r", "")
  }

  def obtainLanguageForFile(file: VirtualFile): Language = {
    if (DecompilerUtil.isScalaFile(file)) {
      ScalaFileType.SCALA_LANGUAGE
    } else null
  }

}