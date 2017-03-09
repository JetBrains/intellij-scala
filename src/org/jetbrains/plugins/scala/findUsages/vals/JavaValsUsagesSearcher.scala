package org.jetbrains.plugins.scala
package findUsages.vals

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{PsiSearchHelper, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.psi.{PsiElement, PsiReference, PsiReferenceExpression}
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.light.{PsiTypedDefinitionWrapper, StaticPsiTypedDefinitionWrapper}

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.09.2009
 */

class JavaValsUsagesSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  def execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    val scope = inReadAction(queryParameters.getEffectiveSearchScope)
    val element = queryParameters.getElementToSearch
    element match {
      case _ if inReadAction(!element.isValid) => true
      case scalaValue(vals, name) =>
        val processor = new TextOccurenceProcessor {
          def execute(element: PsiElement, offsetInElement: Int): Boolean = {
            val references = inReadAction(element.getReferences)
            for (ref <- references) {
              inReadAction {
                ref match {
                  case refElement: PsiReferenceExpression if ref.getRangeInElement.contains(offsetInElement) =>
                    refElement.resolve match {
                      case f: FakePsiMethod if f.navElement == vals =>
                        if (!consumer.process(refElement)) return false
                      case t: StaticPsiTypedDefinitionWrapper if t.delegate == vals =>
                        if (!consumer.process(refElement)) return false
                      case t: PsiTypedDefinitionWrapper if t.delegate == vals =>
                        if (!consumer.process(refElement)) return false
                      case _ =>
                    }
                  case _ =>
                }
              }
            }
            true
          }
        }
        val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(queryParameters.getProject)
        helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
      case wrapper: PsiTypedDefinitionWrapper => //only this is added for find usages factory
        val name: String = inReadAction(wrapper.getName)
        val processor = new TextOccurenceProcessor {
          def execute(element: PsiElement, offsetInElement: Int): Boolean = {
            val references = inReadAction(element.getReferences)
            for (ref <- references) {
              inReadAction {
                ref match {
                  case refElement: PsiReferenceExpression if ref.getRangeInElement.contains(offsetInElement) =>
                    refElement.resolve match {
                      case t: PsiTypedDefinitionWrapper if t.delegate == wrapper.delegate &&
                        t.getName == wrapper.getName =>
                        if (!consumer.process(refElement)) return false
                      case t: StaticPsiTypedDefinitionWrapper if t.delegate == wrapper.delegate &&
                        t.getName == wrapper.getName =>
                        if (!consumer.process(refElement)) return false
                      case _ =>
                    }
                  case _ =>
                }
              }
            }
            true
          }
        }
        val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(queryParameters.getProject)
        helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
      case _ => true
    }
  }

  private object scalaValue {
    def unapply(td: ScTypedDefinition): Option[(ScTypedDefinition, String)] = inReadAction {
      ScalaPsiUtil.nameContext(td) match {
        case _: ScValue | _: ScVariable | _: ScClassParameter if td.getName != "" => Some(td, td.getName)
        case _ => None
      }
    }
  }
}