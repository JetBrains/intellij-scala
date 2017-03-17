package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester

import java.util

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.refactoring.rename.NameSuggestionProvider
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester._

/**
  * User: Alexander Podkhalyuzin
  * Date: 23.11.2008
  */
class ScalaNameSuggestionProvider extends NameSuggestionProvider {
  def completeName(element: PsiElement, nameSuggestionContext: PsiElement, prefix: String): util.Collection[LookupElement] = null

  def getSuggestedNames(element: PsiElement, nameSuggestionContext: PsiElement, result: util.Set[String]): SuggestedNameInfo = {
    val names = element match {
      case definition: ScTemplateDefinition => Seq(definition.name)
      case typed: ScTypedDefinition =>
        typed.name +: suggestNamesByType(typed.getType().getOrAny)
      case named: PsiNamedElement => Seq(named.name)
      case expr: ScExpression => suggestNames(expr)
      case _ => Seq.empty
    }

    import scala.collection.JavaConversions._
    result.addAll(names)

    new SuggestedNameInfo(names.toArray) {}
  }
}