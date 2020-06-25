package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion._
import com.intellij.patterns.PlatformPatterns

/**
  * @author Alexander Podkhalyuzin
  *         Date: 16.05.2008
  */
class ScalaBasicCompletionContributor extends ScalaCompletionContributor {

  extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement(),
    new ScalaBasicCompletionProvider
  )

  override def beforeCompletion(context: CompletionInitializationContext): Unit = {
    context.setDummyIdentifier(dummyIdentifier(context.getFile, context.getStartOffset - 1))
    super.beforeCompletion(context)
  }
}