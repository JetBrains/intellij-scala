package org.jetbrains.plugins.scala
package decompiler

import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.{ContentBasedClassFileProcessor, StdFileTypes}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighter
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

/**
 * @author Alefas
 * @since 24/09/14.
 */
class ScalaContentBasedClassFileProcessor extends ContentBasedClassFileProcessor {
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
    val treatDocCommentAsBlockComment = ScalaProjectSettings.getInstance(project).isTreatDocCommentAsBlockComment
    new ScalaSyntaxHighlighter(treatDocCommentAsBlockComment)
  }

  def obtainFileText(project: Project, file: VirtualFile): String = ""

  def obtainLanguageForFile(file: VirtualFile): Language = {
    if (DecompilerUtil.isScalaFile(file)) ScalaFileType.SCALA_LANGUAGE
    else null
  }
}
