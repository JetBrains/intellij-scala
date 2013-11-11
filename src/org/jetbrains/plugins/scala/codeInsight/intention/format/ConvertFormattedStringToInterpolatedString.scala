package org.jetbrains.plugins.scala
package codeInsight.intention.format

import org.jetbrains.plugins.scala.format.{InterpolatedStringFormatter, FormattedStringParser}
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import configuration._

/**
 * Pavel Fatin
 */

class ConvertFormattedStringToInterpolatedString extends AbstractFormatConversionIntention(
  "Convert to interpolated string", FormattedStringParser, InterpolatedStringFormatter) {

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    if (!super.isAvailable(project: Project, editor: Editor, element: PsiElement)) return false
    element.languageLevel.isSinceScala2_10
  }
}
