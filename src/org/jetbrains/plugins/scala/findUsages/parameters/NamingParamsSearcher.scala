package org.jetbrains.plugins.scala
package findUsages
package parameters

import com.intellij.psi._
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{PsiSearchHelper, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScAssignStmt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

import scala.collection.mutable

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.08.2009
 */
class NamingParamsSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  def execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    val scope = inReadAction(queryParameters.getEffectiveSearchScope)
    val element = queryParameters.getElementToSearch
    element match {
      case _ if !inReadAction(element.isValid) => true
      case parameter : ScParameter =>
        val name = parameter.name
        val collectedReferences = new mutable.HashSet[PsiReference]
        val processor = new TextOccurenceProcessor {
          def execute(element: PsiElement, offsetInElement: Int): Boolean = {
            val references = inReadAction(element.getReferences)
            for (ref <- references if ref.getRangeInElement.contains(offsetInElement) && !collectedReferences.contains(ref)) {
              ref match {
                case refElement: ScReferenceElement =>
                  inReadAction {
                    refElement.getParent match {
                      case assign: ScAssignStmt if assign.getLExpression == refElement &&
                        assign.getParent.isInstanceOf[ScArgumentExprList] =>
                        Option(refElement.resolve()) match {
                          case Some(`parameter`) => if (!consumer.process(ref)) return false
                          case Some(x: ScParameter) =>
                            ScalaPsiUtil.parameterForSyntheticParameter(x) match {
                              case Some(realParam) =>
                                if (realParam == parameter && !consumer.process(ref)) return false
                              case None =>
                            }
                          case _ =>
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
        val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(queryParameters.getProject)
        helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
      case _ => true
    }
  }
}
