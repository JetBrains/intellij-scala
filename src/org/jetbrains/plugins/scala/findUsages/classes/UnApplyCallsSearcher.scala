package org.jetbrains.plugins.scala.findUsages.classes

import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import com.intellij.util.{Query, Processor, QueryExecutor}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import com.intellij.psi._
import search.{UsageSearchContext, PsiSearchHelper, TextOccurenceProcessor}
import collection.mutable.{HashSet, ArrayBuffer}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.08.2009
 */

class UnApplyCallsSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  def execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    val scope = queryParameters.getEffectiveSearchScope
    val element = queryParameters.getElementToSearch
    val ignoreAccess = queryParameters.isIgnoreAccessScope
    element match {
      case td: ScTypeDefinition => {
        val name = td.getName
        val qualifier = td.getQualifiedName
        val collectedReferences = new HashSet[PsiReference]
        def process(ref: PsiReference): Boolean = {
          ref match {
            case ref: ScReferenceElement => {
              if (ref.refName == name) {
                consumer.process(ref)
              }
              else true
            }
            case _ => true
          }
        }
        def getProcessor(method: PsiMethod) = new TextOccurenceProcessor {
          def execute(element: PsiElement, offsetInElement: Int): Boolean = {
            val references = element.getReferences
            for (ref <- references if ref.getRangeInElement.contains(offsetInElement) && !collectedReferences.contains(ref)) {
              ref match {
                case refElement: ScReferenceElement => {
                  for (reference <- refElement.multiResolve(true)) {
                    reference match {
                      case ScalaResolveResult(psiMethod: PsiMethod, _) if psiMethod == method => {
                        collectedReferences += ref
                        if (!process(ref)) return false
                      }
                      case _ =>
                    }
                  }
                }
                case _ =>
              }
            }
            return true
          }
        }
        val helper: PsiSearchHelper = PsiManager.getInstance(td.getProject).getSearchHelper
        //todo: think about cases, when class name is 'apply' etc.
        for (method <- td.functionsByName("apply")) {
          helper.processElementsWithWord(getProcessor(method), scope, name, UsageSearchContext.IN_CODE, false)
        }
        for (method <- td.functionsByName("unapply")) {
          helper.processElementsWithWord(getProcessor(method), scope, name, UsageSearchContext.IN_CODE, false)
        }
        for (method <- td.functionsByName("unapplySeq")) {
          helper.processElementsWithWord(getProcessor(method), scope, name, UsageSearchContext.IN_CODE, false)
        }
      }
      case _ =>
    }
    true
  }
}
