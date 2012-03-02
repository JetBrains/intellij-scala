package org.jetbrains.plugins.scala.findUsages.vals

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariable, ScValue}
import collection.mutable.HashSet
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import com.intellij.psi.search.{PsiSearchHelper, UsageSearchContext, TextOccurenceProcessor}
import com.intellij.psi.{PsiReferenceExpression, PsiElement, PsiReference}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.lang.psi.light.{StaticPsiTypedDefinitionWrapper, PsiTypedDefinitionWrapper}

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.09.2009
 */

class JavaValsUsagesSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  def execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    val scope = queryParameters.getScope
    val element = queryParameters.getElementToSearch
    ApplicationManager.getApplication.runReadAction(new Computable[Boolean] {
      def compute: Boolean = {
        element match {
          case vals: ScTypedDefinition if ScalaPsiUtil.nameContext(vals).isInstanceOf[ScValue] ||
                  ScalaPsiUtil.nameContext(vals).isInstanceOf[ScVariable] =>
            val name: String = vals.getName
            val collectedReferences: HashSet[PsiReference] = new HashSet[PsiReference]
            val processor = new TextOccurenceProcessor {
              def execute(element: PsiElement, offsetInElement: Int): Boolean = {
                val references = element.getReferences
                for (ref <- references if ref.getRangeInElement.contains(offsetInElement) && !collectedReferences.contains(ref)) {
                  ref match {
                    case refElement: PsiReferenceExpression => {
                      refElement.resolve match {
                        case f: FakePsiMethod if f.navElement == vals =>
                          if (!consumer.process(refElement)) return false
                        case t: PsiTypedDefinitionWrapper if t.getNavigationElement == vals =>
                          if (!consumer.process(refElement)) return false
                        case t: StaticPsiTypedDefinitionWrapper if t.getNavigationElement == vals =>
                          if (!consumer.process(refElement)) return false
                        case _ =>
                      }
                    }
                    case _ =>
                  }
                }
                true
              }
            }
            val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(vals.getProject)
            helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
          case wrapper: PsiTypedDefinitionWrapper => //only this is added for find usages factory
            val name: String = wrapper.getName
            val collectedReferences: HashSet[PsiReference] = new HashSet[PsiReference]
            val processor = new TextOccurenceProcessor {
              def execute(element: PsiElement, offsetInElement: Int): Boolean = {
                val references = element.getReferences
                for (ref <- references if ref.getRangeInElement.contains(offsetInElement) && !collectedReferences.contains(ref)) {
                  ref match {
                    case refElement: PsiReferenceExpression => {
                      refElement.resolve match {
                        case t: PsiTypedDefinitionWrapper if t.getNavigationElement == wrapper.getNavigationElement &&
                          t.getName == wrapper.getName =>
                          if (!consumer.process(refElement)) return false
                        case t: StaticPsiTypedDefinitionWrapper if t.getNavigationElement == t.getNavigationElement &&
                          t.getName == wrapper.getName =>
                          if (!consumer.process(refElement)) return false
                        case _ =>
                      }
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
    })
  }
}