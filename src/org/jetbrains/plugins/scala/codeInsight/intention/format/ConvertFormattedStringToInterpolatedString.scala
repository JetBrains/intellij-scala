package org.jetbrains.plugins.scala.codeInsight.intention.format

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.format.{FormattedStringParser, InterpolatedStringFormatter}
import org.jetbrains.plugins.scala.lang.languageLevel.ScalaLanguageLevel

/**
 * Pavel Fatin
 */

class ConvertFormattedStringToInterpolatedString extends AbstractFormatConversionIntention(
  "Convert to interpolated string", FormattedStringParser, InterpolatedStringFormatter) {

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    if (!super.isAvailable(project: Project, editor: Editor, element: PsiElement)) return false
    ScalaLanguageLevel.getLanguageLevel(element).isThoughScala2_10
  }
}
