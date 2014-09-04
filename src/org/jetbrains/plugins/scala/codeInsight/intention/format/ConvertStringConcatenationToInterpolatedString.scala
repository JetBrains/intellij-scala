package org.jetbrains.plugins.scala.codeInsight.intention.format

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.format.{InterpolatedStringFormatter, StringConcatenationParser}
import org.jetbrains.plugins.scala.lang.languageLevel.ScalaLanguageLevel

/**
 * Pavel Fatin
 */

class ConvertStringConcatenationToInterpolatedString extends AbstractFormatConversionIntention(
  "Convert to interpolated string", StringConcatenationParser, InterpolatedStringFormatter, eager = true) {

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    if (!super.isAvailable(project: Project, editor: Editor, element: PsiElement)) return false
    ScalaLanguageLevel.getLanguageLevel(element).isThoughScala2_10
  }
}
