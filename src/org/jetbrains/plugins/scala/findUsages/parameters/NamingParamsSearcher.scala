package org.jetbrains.plugins.scala
package findUsages
package parameters

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import com.intellij.psi._
import search.{UsageSearchContext, PsiSearchHelper, TextOccurenceProcessor}
import collection.mutable.HashSet
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScAssignStmt}
import lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.extensions.inReadAction

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.08.2009
 */

class NamingParamsSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  def execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    val scope = queryParameters.getScope
    val element = queryParameters.getElementToSearch
    inReadAction {
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
                        Option(refElement.resolve()) match {
                          case Some(`parameter`) => if (!consumer.process(ref)) return false
                          case Some(x: ScParameter) =>
                            ScalaPsiUtil.parameterForSyntheticParameter(x) match {
                              case Some(realParam) => if (!consumer.process(ref)) return false
                              case None =>
                            }
                          case _ =>
                        }
                      }
                      case _ =>
                    }
                  }
                  case _ =>
                }
              }
              true
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
}
