package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.psi.PsiElement

import com.intellij.refactoring.rename.NameSuggestionProvider
import psi.api.expr.ScExpression
import psi.api.toplevel.ScTypedDefinition
import psi.types.result.TypingContext
import psi.types._
import java.lang.String
import java.util.{Set, Collection, List}

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.11.2008
 */

class ScalaNameSuggestionProvider extends NameSuggestionProvider {
  def completeName(element: PsiElement, nameSuggestionContext: PsiElement, prefix: String): Collection[LookupElement] = null

  def getSuggestedNames(element: PsiElement, nameSuggestionContext: PsiElement, result: Set[String]): SuggestedNameInfo = {
    val array = element match {
      case typed: ScTypedDefinition => NameSuggester.suggestNamesByType(typed.getType(TypingContext.empty).getOrAny)
      case expr: ScExpression => NameSuggester.suggestNames(expr)
      case _ => Array[String]()
    }
    for (name <- array) result.add(name)
    return new SuggestedNameInfo(array) {
      def nameChoosen(name: String): Unit = {}
    }
  }
}