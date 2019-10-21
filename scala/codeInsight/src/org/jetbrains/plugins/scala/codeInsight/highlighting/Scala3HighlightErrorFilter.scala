package org.jetbrains.plugins.scala
package codeInsight
package highlighting

import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.psi.PsiErrorElement

final class Scala3HighlightErrorFilter extends HighlightErrorFilter {

  override def shouldHighlightErrorElement(element: PsiErrorElement): Boolean =
    !Option(element.getContainingFile)
      .map(_.getLanguage)
      .contains(Scala3Language.INSTANCE)
}
