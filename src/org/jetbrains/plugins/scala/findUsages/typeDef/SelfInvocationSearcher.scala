package org.jetbrains.plugins.scala.findUsages.typeDef

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{LocalSearchScope, PsiSearchHelper, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScSelfInvocation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

/**
  * Nikolay.Tropin
  * 21-Feb-17
  */
class SelfInvocationSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  override def execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    queryParameters.getElementToSearch match {
      case ml: ScMethodLike if inReadAction(ml.isConstructor) =>
        val localScope = inReadAction {
          Option(ml.containingClass).map(new LocalSearchScope(_))
        }

        localScope.forall { scope =>
          val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(queryParameters.getProject)
          val processor = new TextOccurenceProcessor {
            def execute(element: PsiElement, offsetInElement: Int): Boolean = {
              inReadAction {
                element match {
                  case si: ScSelfInvocation if si.bind.contains(ml) => consumer.process(si)
                  case _ => true
                }
              }
            }
          }
          helper.processElementsWithWord(processor, scope, "this", UsageSearchContext.IN_CODE, true)
        }
      case _ => true
    }
  }
}
