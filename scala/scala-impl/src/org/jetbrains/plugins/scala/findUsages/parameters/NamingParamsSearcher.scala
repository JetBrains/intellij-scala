package org.jetbrains.plugins.scala
package findUsages
package parameters

import com.intellij.psi._
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{PsiSearchHelper, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.finder.ScalaFilterScope
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
    val project = queryParameters.getProject
    val scope = inReadAction(ScalaFilterScope(queryParameters))

    val data = inReadAction {
      queryParameters.getElementToSearch match {
        case e if !e.isValid => None
        case parameter: ScParameter => Some((parameter, parameter.name))
        case _ => None
      }
    }
    data match {
      case Some((parameter, name)) =>
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
        val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(project)
        helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
      case _ => true
    }
  }
}
