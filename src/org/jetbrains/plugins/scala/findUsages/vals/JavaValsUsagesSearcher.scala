package org.jetbrains.plugins.scala.findUsages.vals

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariable, ScValue}
import collection.mutable.HashSet
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignStmt, ScArgumentExprList}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import com.intellij.psi.search.{PsiSearchHelper, UsageSearchContext, TextOccurenceProcessor}
import com.intellij.psi.{PsiManager, PsiReferenceExpression, PsiElement, PsiReference}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.09.2009
 */

class JavaValsUsagesSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  def execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    val scope = queryParameters.getScope
    val element = queryParameters.getElementToSearch
    val ignoreAccess = queryParameters.isIgnoreAccessScope
    ApplicationManager.getApplication.runReadAction(new Computable[Boolean] {
      def compute: Boolean = {
        element match {
          case vals: ScTypedDefinition if ScalaPsiUtil.nameContext(vals).isInstanceOf[ScValue] ||
                  ScalaPsiUtil.nameContext(vals).isInstanceOf[ScVariable] => {
            val name: String = vals.getName
            val collectedReferences: HashSet[PsiReference] = new HashSet[PsiReference]
            val processor = new TextOccurenceProcessor {
              def execute(element: PsiElement, offsetInElement: Int): Boolean = {
                val references = element.getReferences
                for (ref <- references if ref.getRangeInElement.contains(offsetInElement) && !collectedReferences.contains(ref)) {
                  ref match {
                    case refElement: PsiReferenceExpression => {
                      refElement.resolve match {
                        case f: FakePsiMethod if f.navElement == vals => {
                          if (!consumer.process(refElement)) return false
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
            val helper: PsiSearchHelper = PsiManager.getInstance(vals.getProject).getSearchHelper
            helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
          }
          case _ =>
        }
        true
      }
    })
  }
}