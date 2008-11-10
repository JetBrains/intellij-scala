package org.jetbrains.plugins.scala.lang.psi.impl.search

import com.intellij.psi.{PsiMember, PsiMethod}
import com.intellij.util.{QueryExecutor, Processor}

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.11.2008
 */

class ScalaOverridingMemberSearch extends QueryExecutor[PsiMember, OverridingMemberSearch.SearchParameters]{
  def execute(queryParameters: OverridingMemberSearch.SearchParameters, consumer: Processor[PsiMember]): Boolean = {
    false
  }
}