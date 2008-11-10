package org.jetbrains.plugins.scala.lang.psi.impl.search

import com.intellij.psi.search.searches.{ExtensibleQueryFactory, OverridingMethodsSearch}
import com.intellij.psi.search.SearchScope
import com.intellij.psi.{PsiMember, PsiMethod}
import com.intellij.util.{EmptyQuery, Query}

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.11.2008
 */

object OverridingMemberSearch extends ExtensibleQueryFactory[PsiMember, OverridingMemberParameters.SearchParameters] {
  import OverridingMemberParameters.SearchParameters
  def search(member: PsiMember, scope: SearchScope, checkDeep: Boolean): Query[PsiMember] = {
    if (cannotBeOverriden(member)) EmptyQuery.getEmptyQuery[PsiMember]
    createUniqueResultsQuery(new SearchParameters(member, scope, checkDeep))
  }

  private def cannotBeOverriden(member: PsiMember): Boolean = {
    //todo
    true
  }

  def search(member: PsiMember, checkDeep: Boolean): Query[PsiMember] = search(member, member.getUseScope(), checkDeep)

  def search(member: PsiMember): Query[PsiMember] = search(member, true)
}

object OverridingMemberParameters {
  case class SearchParameters(val member: PsiMember, val searchScope: SearchScope, val checkDeep: Boolean)
}