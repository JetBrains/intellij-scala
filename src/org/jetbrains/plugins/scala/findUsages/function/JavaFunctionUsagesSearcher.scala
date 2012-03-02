package org.jetbrains.plugins.scala.findUsages.function

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.{Processor, QueryExecutor}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import collection.mutable.HashSet
import com.intellij.psi.search.{UsageSearchContext, PsiSearchHelper, TextOccurenceProcessor}
import com.intellij.psi.{PsiMethod, PsiReferenceExpression, PsiElement, PsiReference}
import org.jetbrains.plugins.scala.lang.psi.light.{StaticPsiMethodWrapper, ScFunctionWrapper}

/**
 * @author Alefas
 * @since 28.02.12
 */
class JavaFunctionUsagesSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  def execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    val scope = queryParameters.getScope
    val element = queryParameters.getElementToSearch
    ApplicationManager.getApplication.runReadAction(new Computable[Boolean] {
      def compute: Boolean = {
        element match {
          case p: PsiMethod => {
            val name: String = p.getName
            val collectedReferences: HashSet[PsiReference] = new HashSet[PsiReference]
            val processor = new TextOccurenceProcessor {
              def execute(element: PsiElement, offsetInElement: Int): Boolean = {
                val references = element.getReferences
                for (ref <- references if ref.getRangeInElement.contains(offsetInElement) && !collectedReferences.contains(ref)) {
                  ref match {
                    case refElement: PsiReferenceExpression => {
                      refElement.resolve match {
                        case f: ScFunctionWrapper if f.function == p =>
                          if (!consumer.process(refElement)) return false
                        case t: StaticPsiMethodWrapper if t.getNavigationElement == p =>
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
            val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(p.getProject)
            helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
          }
          case _ =>
        }
        true
      }
    })
  }
}
