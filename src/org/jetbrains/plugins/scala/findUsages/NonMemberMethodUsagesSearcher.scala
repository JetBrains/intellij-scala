package org.jetbrains.plugins.scala
package findUsages

import com.intellij.psi._
import impl.search.{SimpleAccessorReferenceSearcher, MethodTextOccurrenceProcessor, ConstructorReferencesSearchHelper}
import search.searches.{MethodReferencesSearch, ReferencesSearch}
import search.{UsageSearchContext, SearchScope, SearchRequestCollector}
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.util.Processor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.util.ScalaUtils

/**
 * Searches for scala methods defined inside of local scopes, ie methods for which .getContainingClass == null.
 *
 * These are not considered by [com.intellij.psi.impl.search.MethodUsagesSearcher]
 */
class NonMemberMethodUsagesSearcher extends QueryExecutorBase[PsiReference, MethodReferencesSearch.SearchParameters] {
  def processQuery(@NotNull p: MethodReferencesSearch.SearchParameters, @NotNull consumer: Processor[PsiReference]) {
    val method: PsiMethod = p.getMethod
    val collector: SearchRequestCollector = p.getOptimizer
    val searchScope: SearchScope = p.getScope
    val newConsumer = new Processor[PsiReference] {
      def process(t: PsiReference): Boolean = {
        if (!method.isConstructor) return true
        consumer.process(t)
      }
    }
    ReferencesSearch.searchOptimized(method, searchScope, false, collector, newConsumer)
  }
}

