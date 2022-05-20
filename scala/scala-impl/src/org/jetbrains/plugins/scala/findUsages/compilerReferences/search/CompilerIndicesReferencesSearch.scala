package org.jetbrains.plugins.scala.findUsages.compilerReferences.search

import com.intellij.psi.search.SearchScope
import com.intellij.psi.{PsiNamedElement, PsiReference}
import com.intellij.util.{Query, QueryExecutor, QueryFactory}
import org.jetbrains.plugins.scala.extensions._

import java.util
import java.util.Collections

class CompilerIndicesReferencesSearch private ()
    extends QueryFactory[PsiReference, CompilerIndicesReferencesSearch.SearchParameters] {

  override def getExecutors: util.List[QueryExecutor[PsiReference, CompilerIndicesReferencesSearch.SearchParameters]] =
    Collections.singletonList(new CompilerIndicesReferencesSearcher)
}

object CompilerIndicesReferencesSearch {
  private[this] val instance = new CompilerIndicesReferencesSearch

  def search(
    target:          PsiNamedElement,
    scope:           SearchScope,
    includeExplicit: Boolean = true,
  ): Query[PsiReference] =
    instance.createUniqueResultsQuery(SearchParameters(target, scope, includeExplicit))

  def search(element: PsiNamedElement): Query[PsiReference] =
    search(element, inReadAction(element.getUseScope))

  final case class SearchParameters(
    element:         PsiNamedElement,
    searchScope:     SearchScope,
    includeExplicit: Boolean,
  )
}
