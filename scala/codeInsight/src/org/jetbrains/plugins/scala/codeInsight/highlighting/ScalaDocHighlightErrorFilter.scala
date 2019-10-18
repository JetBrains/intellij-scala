package org.jetbrains.plugins.scala
package codeInsight
package highlighting

import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.psi.PsiErrorElement
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

final class ScalaDocHighlightErrorFilter extends HighlightErrorFilter {

  override def shouldHighlightErrorElement(element: PsiErrorElement): Boolean = !isInScaladoc(element)

  private def isInScaladoc(element: PsiErrorElement) =
    element.parentsInFile.instancesOf[ScDocComment].nonEmpty
}
