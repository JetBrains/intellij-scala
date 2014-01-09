package org.jetbrains.plugins.scala.findUsages.function

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.{Processor, QueryExecutor}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.search.{UsageSearchContext, PsiSearchHelper, TextOccurenceProcessor}
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.light.{StaticPsiMethodWrapper, ScFunctionWrapper}
import scala.collection.mutable
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * @author Alefas
 * @since 28.02.12
 */
class JavaFunctionUsagesSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  def execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    ApplicationManager.getApplication.runReadAction(new Computable[Boolean] {
      def compute: Boolean = {
        val scope = queryParameters.getEffectiveSearchScope
        val element = queryParameters.getElementToSearch
        if (!element.isValid) return true
        element match {
          case method: PsiMethod if method.isInstanceOf[ScFunction] || !method.hasModifierProperty(PsiModifier.STATIC) =>
            val name: String = method.getName
            val collectedReferences: mutable.HashSet[PsiReference] = new mutable.HashSet[PsiReference]
            val processor = new TextOccurenceProcessor {
              def execute(element: PsiElement, offsetInElement: Int): Boolean = {
                val references = element.getReferences
                for (ref <- references if ref.getRangeInElement.contains(offsetInElement) && !collectedReferences.contains(ref)) {
                  ref match {
                    case refElement: PsiReferenceExpression =>
                      refElement.resolve match {
                        case f: ScFunctionWrapper if f.function == method && !consumer.process(refElement) => return false
                        case t: StaticPsiMethodWrapper if t.getNavigationElement == method && !consumer.process(refElement) => return false
                        case _ =>
                      }
                    case _ =>
                  }
                }
                true
              }
            }
            val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(method.getProject)
            if (name == "") return true
            helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
          case _ =>
        }
        true
      }
    })
  }
}
