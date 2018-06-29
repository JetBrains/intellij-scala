package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.psi.PsiErrorElement
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

class ScaladocHighlightErrorFilter extends HighlightErrorFilter {

  override def shouldHighlightErrorElement(element: PsiErrorElement): Boolean = !isInScaladoc(element)

  private def isInScaladoc(element: PsiErrorElement) =
    element.parentsInFile.filterByType[ScDocComment].nonEmpty
}
