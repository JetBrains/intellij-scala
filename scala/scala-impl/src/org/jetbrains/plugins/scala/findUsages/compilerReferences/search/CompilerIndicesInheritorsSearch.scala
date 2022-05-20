package org.jetbrains.plugins.scala.findUsages.compilerReferences
package search

import com.intellij.psi.search.SearchScope
import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.util.{Query, QueryExecutor, QueryFactory}
import org.jetbrains.plugins.scala.extensions.inReadAction

import java.util
import java.util.Collections

class CompilerIndicesInheritorsSearch private ()
  extends QueryFactory[PsiElement, CompilerIndicesInheritorsSearch.SearchParameters] {

  override def getExecutors: util.List[QueryExecutor[PsiElement, CompilerIndicesInheritorsSearch.SearchParameters]] =
    Collections.singletonList(new CompilerIndicesInheritorsSearcher)
}

object CompilerIndicesInheritorsSearch {
  private[this] val instance = new CompilerIndicesInheritorsSearch

  def search(
    cls:       PsiClass,
    scope:     SearchScope,
    checkDeep: Boolean = false,
  ): Query[PsiElement] =
    instance.createUniqueResultsQuery(SearchParameters(cls, scope, checkDeep))

  def search(cls: PsiClass): Query[PsiElement] =
    search(cls, inReadAction(cls.getUseScope))

  final case class SearchParameters(
    cls:         PsiClass,
    searchScope: SearchScope,
    checkDeep:   Boolean
  )
}
