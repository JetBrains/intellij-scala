package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester

import java.util

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.refactoring.rename.NameSuggestionProvider
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.11.2008
 */

class ScalaNameSuggestionProvider extends NameSuggestionProvider {
  def completeName(element: PsiElement, nameSuggestionContext: PsiElement, prefix: String): util.Collection[LookupElement] = null

  def getSuggestedNames(element: PsiElement, nameSuggestionContext: PsiElement, result: util.Set[String]): SuggestedNameInfo = {
    val names = element match {
      case clazz: ScTemplateDefinition => Seq[String](clazz.name)
      case typed: ScTypedDefinition => typed.name +: NameSuggester.suggestNamesByType(typed.getType(TypingContext.empty).getOrAny).toSeq
      case expr: ScExpression => NameSuggester.suggestNames(expr).toSeq
      case named: ScNamedElement => Seq[String](named.name)
      case named: PsiNamedElement => Seq[String](named.getName)
      case _ => Seq[String]()
    }
    names.distinct.foreach(result.add)
    new SuggestedNameInfo(names.toArray) {}
  }
}