package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.lang.LanguageFormattingRestriction
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScFile

class ScalaLanguageFormattingRestriction extends LanguageFormattingRestriction {

  override def isFormatterAllowed(context: PsiElement): Boolean = true

  override def isAutoFormatAllowed(context: PsiElement): Boolean = {
    context.getContainingFile match {
      case file: ScFile if !file.isCompiled =>
        val vFile = file.getVirtualFile
        if (vFile == null) {
          true
        } else {
          val project = file.getProject
          val index = ProjectFileIndex.getInstance(project)

          index.isInSourceContent(vFile)
        }
      case _ => true
    }
  }
}
