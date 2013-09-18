package org.jetbrains.plugins.scala
package findUsages
package setter

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.{Processor, QueryExecutor}
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import com.intellij.psi.search.{TextOccurenceProcessor, PsiSearchHelper, UsageSearchContext}
import extensions.{inReadAction, Parent}
import lang.psi.api.expr.ScAssignStmt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement


class SetterMethodSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  def execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    val scope = queryParameters.getEffectiveSearchScope
    val element = queryParameters.getElementToSearch
    val SetterSuffix = "_="
    inReadAction {
      if (element.isValid) {
        element match {
          case fun: ScFunction if fun.name endsWith SetterSuffix =>
            val processor = new TextOccurenceProcessor {
              def execute(element: PsiElement, offsetInElement: Int): Boolean = {
                element match {
                  case Parent(Parent(assign: ScAssignStmt)) => assign.resolveAssignment match {
                    case Some(res) if res.element.getNavigationElement == fun =>
                      Option(assign.getLExpression).foreach {
                        case ref: ScReferenceElement => if (!consumer.process(ref)) return false
                      }
                    case _ =>
                  }
                  case _ =>
                }
                true
              }
            }

            val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(fun.getProject)
            helper.processElementsWithWord(processor, scope, fun.name.stripSuffix(SetterSuffix), UsageSearchContext.IN_CODE, true)
          case _ =>
        }
      }
    }
    true
  }
}
