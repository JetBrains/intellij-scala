package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.psi.PsiErrorElement
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode

private final class CompilerHighlightingErrorFilter extends HighlightErrorFilter {
  override def shouldHighlightErrorElement(element: PsiErrorElement): Boolean =
    !ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(element.getContainingFile)
}
