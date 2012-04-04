package org.jetbrains.plugins.scala
package findUsages
package setter

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.{Processor, QueryExecutor}
import com.intellij.psi._
import lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.util.ScalaUtil
import com.intellij.psi.search.{TextOccurenceProcessor, PsiSearchHelper, UsageSearchContext}
import extensions.Parent
import lang.psi.api.expr.ScAssignStmt
import lang.resolve.ResolvableReferenceElement


class SetterMethodSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  def execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    val scope = queryParameters.getScope
    val element = queryParameters.getElementToSearch
    val ignoreAccess = queryParameters.isIgnoreAccessScope
    val SetterSuffix = "_="
    ScalaUtil.readAction(element.getProject) {
      element match {
        case fun: ScFunctionDefinition if fun.name endsWith SetterSuffix =>
          val processor = new TextOccurenceProcessor {
            def execute(element: PsiElement, offsetInElement: Int): Boolean = {
              element match {
                case Parent(Parent(assign: ScAssignStmt)) => assign.getLExpression match {
                  case ref: ResolvableReferenceElement => ref.bind() match {
                    case Some(x) if x.element.getNavigationElement == fun =>
                      if (!consumer.process(ref)) return false
                    case _ =>
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
    true
  }
}
