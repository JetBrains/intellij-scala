package org.jetbrains.plugins.scala
package findUsages.vals

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{PsiSearchHelper, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.psi.{PsiElement, PsiReference, PsiReferenceExpression}
import com.intellij.util.{Processor, QueryExecutor}
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
    extensions.inReadAction {
      val scope = queryParameters.getEffectiveSearchScope
      val element = queryParameters.getElementToSearch
      if (!element.isValid) return true
      element match {
        case vals: ScTypedDefinition => ScalaPsiUtil.nameContext(vals) match {
          case _: ScValue | _: ScVariable | _: ScClassParameter if vals.getName != "" =>
            val name: String = vals.getName
            val processor = new TextOccurenceProcessor {
              def execute(element: PsiElement, offsetInElement: Int): Boolean = {
                val references = element.getReferences
                for (ref <- references if ref.getRangeInElement.contains(offsetInElement)) {
                  ref match {
                    case refElement: PsiReferenceExpression =>
                      refElement.resolve match {
                        case f: FakePsiMethod if f.navElement == vals =>
                          if (!consumer.process(refElement)) return false
                        case t: StaticPsiTypedDefinitionWrapper if t.typedDefinition == vals =>
                          if (!consumer.process(refElement)) return false
                        case t: PsiTypedDefinitionWrapper if t.typedDefinition == vals =>
                          if (!consumer.process(refElement)) return false
                        case _ =>
                      }
                    case _ =>
                  }
                }
                true
              }
            }
            val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(vals.getProject)
            helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
          case _ =>
        }
        case wrapper: PsiTypedDefinitionWrapper => //only this is added for find usages factory
          val name: String = wrapper.getName
          val processor = new TextOccurenceProcessor {
            def execute(element: PsiElement, offsetInElement: Int): Boolean = {
              val references = element.getReferences
              for (ref <- references if ref.getRangeInElement.contains(offsetInElement)) {
                ref match {
                  case refElement: PsiReferenceExpression =>
                    refElement.resolve match {
                      case t: PsiTypedDefinitionWrapper if t.typedDefinition == wrapper.typedDefinition &&
                              t.getName == wrapper.getName =>
                        if (!consumer.process(refElement)) return false
                      case t: StaticPsiTypedDefinitionWrapper if t.typedDefinition == wrapper.typedDefinition &&
                              t.getName == wrapper.getName =>
                        if (!consumer.process(refElement)) return false
                      case _ =>
                    }
                  case _ =>
                }
              }
              true
            }
          }
          val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(wrapper.getProject)
          helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
        case _ =>
      }
      true
    }
  }
}