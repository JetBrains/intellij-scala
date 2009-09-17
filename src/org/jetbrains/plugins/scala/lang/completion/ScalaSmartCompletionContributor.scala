package org.jetbrains.plugins.scala.lang.completion

import com.intellij.patterns.PlatformPatterns
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.util.ProcessingContext
import com.intellij.codeInsight.completion._

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.09.2009
 */

class ScalaSmartCompletionContributor extends CompletionContributor {
  extend(CompletionType.SMART, PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER),
    new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {

      }
    })
}