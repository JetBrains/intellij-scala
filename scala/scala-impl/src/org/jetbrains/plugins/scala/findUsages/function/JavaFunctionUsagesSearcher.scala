package org.jetbrains.plugins.scala.findUsages.function

import com.intellij.psi._
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{PsiSearchHelper, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.light.{ScFunctionWrapper, StaticPsiMethodWrapper}

import scala.collection.mutable

/**
 * @author Alefas
 * @since 28.02.12
 */
class JavaFunctionUsagesSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  def execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    val scope = inReadAction(queryParameters.getEffectiveSearchScope)
    val element = queryParameters.getElementToSearch
    element match {
      case scalaOrNonStatic(method, name) =>
        val collectedReferences: mutable.HashSet[PsiReference] = new mutable.HashSet[PsiReference]
        val processor = new TextOccurenceProcessor {
          def execute(element: PsiElement, offsetInElement: Int): Boolean = {
            val references = inReadAction(element.getReferences)
            for (ref <- references if ref.getRangeInElement.contains(offsetInElement) && !collectedReferences.contains(ref)) {
              inReadAction {
                ref match {
                  case refElement: PsiReferenceExpression =>
                    refElement.resolve match {
                      case ScFunctionWrapper(delegate) if delegate == method && !consumer.process(refElement) => return false
                      case t: StaticPsiMethodWrapper if t.getNavigationElement == method && !consumer.process(refElement) => return false
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
        if (name == "") return true
        helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
      case _ =>
    }
    true

  }

  private object scalaOrNonStatic {
    def unapply(method: PsiMethod): Option[(PsiMethod, String)] = {
      inReadAction {
        if (!method.isValid) return None
        method match {
          case f: ScFunction => Some((f, f.getName))
          case m: PsiMethod if !m.hasModifierProperty(PsiModifier.STATIC) => Some((m, m.getName))
          case _ => None
        }
      }
    }
  }
}
