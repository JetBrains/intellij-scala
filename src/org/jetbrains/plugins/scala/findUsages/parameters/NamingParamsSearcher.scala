package org.jetbrains.plugins.scala.findUsages.parameters

import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import com.intellij.util.{Query, Processor, QueryExecutor}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import com.intellij.psi._
import search.{UsageSearchContext, PsiSearchHelper, TextOccurenceProcessor}
import collection.mutable.{HashSet, ArrayBuffer}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScAssignStmt}

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.08.2009
 */

class NamingParamsSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  def execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    val scope = queryParameters.getScope
    val element = queryParameters.getElementToSearch
    val ignoreAccess = queryParameters.isIgnoreAccessScope
    element match {
      case parameter: ScParameter => {
        val name = parameter.getName
        val collectedReferences = new HashSet[PsiReference]
        val processor = new TextOccurenceProcessor {
          def execute(element: PsiElement, offsetInElement: Int): Boolean = {
            val references = element.getReferences
            for (ref <- references if ref.getRangeInElement.contains(offsetInElement) && !collectedReferences.contains(ref)) {
              ref match {
                case refElement: ScReferenceElement => {
                  refElement.getParent match {
                    case assign: ScAssignStmt if assign.getLExpression == refElement &&
                            assign.getParent.isInstanceOf[ScArgumentExprList] => {
                      if (refElement.resolve == parameter) {
                        if (!consumer.process(ref)) return false
                      }
                    }
                    case _ =>
                  }
                }
                case _ =>
              }
            }
            return true
          }
        }
        val helper: PsiSearchHelper = PsiManager.getInstance(parameter.getProject).getSearchHelper
        helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
      }
      case _ =>
    }
    true
  }
}
