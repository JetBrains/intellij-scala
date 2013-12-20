package org.jetbrains.plugins.scala
package decompiler

import _root_.org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighter


import lang.psi.api.ScalaFile
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.ContentBasedClassFileProcessor
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import settings._
import com.intellij.openapi.application.ApplicationManager

/**
 * @author ilyas
 */
class ScContentBasedClassFileProcessor extends ContentBasedClassFileProcessor {

  def getDecompiledPsiFile(clsFile: PsiFile): PsiFile = null

  def isApplicable(project: Project, vFile: VirtualFile): Boolean = {
    val ft = vFile.getFileType
    if (ft == StdFileTypes.CLASS) {
      val notDisposedProject =
        if (ApplicationManager.getApplication.isUnitTestMode && project.isDisposed) {
          DecompilerUtil.obtainProject
        } else project
      PsiManager.getInstance(notDisposedProject).findFile(vFile) match {
        case scalaFile : ScalaFile => true
        case _ => DecompilerUtil.isScalaFile(vFile)
      }
    } else false
  }

  def createHighlighter(project: Project, file: VirtualFile) = {
    val treatDocCommentAsBlockComment = ScalaProjectSettings.getInstance(project).
            isTreatDocCommentAsBlockComment
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

//todo: API should be changed. We want to have first processor as well as last
//First is for "obtainFileText", last for "createHighlighter"
class ScContentBasedClassFileProcessorHack extends ScContentBasedClassFileProcessor