package org.jetbrains.plugins.scala
package codeInsight
package highlighting

import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.psi.PsiErrorElement
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode

final class ScalaHighlightErrorFilter extends HighlightErrorFilter {

  override def shouldHighlightErrorElement(element: PsiErrorElement): Boolean =
    ScalaHighlightingMode.showParserErrors(element.getContainingFile)
}
