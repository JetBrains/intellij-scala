package org.jetbrains.plugins.scala
package findUsages

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi._
import com.intellij.psi.search.SearchRequestCollector
import com.intellij.psi.search.searches.{MethodReferencesSearch, ReferencesSearch}
import com.intellij.util.Processor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.finder.ScalaFilterScope

/**
 * Searches for scala methods defined inside of local scopes, ie methods for which .getContainingClass == null.
 * These are not considered by [[com.intellij.psi.impl.search.MethodUsagesSearcher]]
 */
class NonMemberMethodUsagesSearcher extends QueryExecutorBase[PsiReference, MethodReferencesSearch.SearchParameters] {
  def processQuery(@NotNull p: MethodReferencesSearch.SearchParameters, @NotNull consumer: Processor[PsiReference]) {
    extensions.inReadAction {
      val method: PsiMethod = p.getMethod
      val collector: SearchRequestCollector = p.getOptimizer
      val newConsumer = new Processor[PsiReference] {
        def process(t: PsiReference): Boolean = {
          if (method.isConstructor) return true
          consumer.process(t)
        }
      }
      ReferencesSearch.searchOptimized(method, ScalaFilterScope(p), false, collector, newConsumer)
    }
  }
}

