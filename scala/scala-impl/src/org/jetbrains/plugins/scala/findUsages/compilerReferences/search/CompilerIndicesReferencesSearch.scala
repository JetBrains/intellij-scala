package org.jetbrains.plugins.scala.findUsages.compilerReferences.search

import java.util
import java.util.Collections

import com.intellij.psi.search.{GlobalSearchScope, SearchScope}
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.{PsiElement, PsiNamedElement, PsiReference}
import com.intellij.util.{EmptyQuery, Query, QueryExecutor, QueryFactory}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.ImplicitUtil.ImplicitSearchTarget

class CompilerIndicesReferencesSearch private ()
    extends QueryFactory[PsiReference, CompilerIndicesReferencesSearch.SearchParameters] {

  override def getExecutors: util.List[QueryExecutor[PsiReference, CompilerIndicesReferencesSearch.SearchParameters]] =
    Collections.singletonList(new CompilerIndicesReferencesSearcher)
}

object CompilerIndicesReferencesSearch {
  private[this] val instance = new CompilerIndicesReferencesSearch

  def search(
    element:         PsiElement,
    scope:           SearchScope,
    includeExplicit: Boolean = true,
  ): Query[PsiReference] = inReadAction {
    element match {
      case ImplicitSearchTarget(target) =>
        instance.createUniqueResultsQuery(SearchParameters(target, scope, includeExplicit))
      case _ => EmptyQuery.getEmptyQuery[PsiReference]
    }
  }

  def search(element: PsiElement): Query[PsiReference] =
    search(element, GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(element)))

  final case class SearchParameters(
    element:         PsiNamedElement,
    searchScope:     SearchScope,
    includeExplicit: Boolean,
  )
}
