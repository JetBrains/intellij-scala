package org.jetbrains.plugins.scala.codeInsight.intention.format

import org.jetbrains.plugins.scala.format.{InterpolatedStringFormatter, FormattedStringParser}
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.openapi.roots.{ProjectRootManager, ProjectFileIndex}
import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.config.ScalaFacet
import org.jetbrains.plugins.scala.lang.languageLevel.ScalaLanguageLevel

/**
 * Pavel Fatin
 */

class ConvertFormattedStringToInterpolatedString extends AbstractFormatConversionIntention(
  "Convert to interpolated string", FormattedStringParser, InterpolatedStringFormatter) {

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val fileIndex: ProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex
    val currentModule: Module = fileIndex.getModuleForFile(element.getContainingFile.getVirtualFile)
    val languageLevel = ScalaFacet.findIn(currentModule).map(_.languageLevel).getOrElse(null)
    if (languageLevel == null || languageLevel == ScalaLanguageLevel.SCALA2_9) return false

    super.isAvailable(project: Project, editor: Editor, element: PsiElement)
  }
}
