package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.psi.{PsiNamedElement, PsiElement}

import com.intellij.refactoring.rename.NameSuggestionProvider
import psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import psi.types.result.TypingContext
import java.lang.String
import java.util
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.11.2008
 */

class ScalaNameSuggestionProvider extends NameSuggestionProvider {
  def completeName(element: PsiElement, nameSuggestionContext: PsiElement, prefix: String): util.Collection[LookupElement] = null

  def getSuggestedNames(element: PsiElement, nameSuggestionContext: PsiElement, result: util.Set[String]): SuggestedNameInfo = {
    result.clear()
    val array = element match {
      case clazz: ScTemplateDefinition => Array[String](clazz.name)
      case typed: ScTypedDefinition => NameSuggester.suggestNamesByType(typed.getType(TypingContext.empty).getOrAny)
      case expr: ScExpression => NameSuggester.suggestNames(expr)
      case named: ScNamedElement => Array[String](named.name)
      case named: PsiNamedElement => Array[String](named.getName)
      case _ => Array[String]()
    }
    for (name <- array) result.add(name)
    new SuggestedNameInfo(array) {}
  }
}