package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.patterns.PlatformPatterns

class ScalaBasicCompletionContributor extends ScalaCompletionContributor {

  extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement(),
    new ScalaBasicCompletionProvider
  )
}
