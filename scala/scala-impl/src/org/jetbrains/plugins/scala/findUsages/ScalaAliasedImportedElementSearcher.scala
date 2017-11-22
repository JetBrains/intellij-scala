package org.jetbrains.plugins.scala
package findUsages

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.search._
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.finder.ScalaFilterScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector


class ScalaAliasedImportedElementSearcher extends QueryExecutorBase[PsiReference, ReferencesSearch.SearchParameters](true) {

  def processQuery(parameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]) {
    val data: Option[(PsiNamedElement, String, SearchScope)] = inReadAction {
      parameters.getElementToSearch match {
        case named: PsiNamedElement =>
          val name = named.name
          ScalaPsiUtil.nameContext(named) match {
            case _: PsiNamedElement | _: PsiMember | _: ScTypeAlias if name != null && !StringUtil.isEmptyOrSpaces(name) =>
              val scope = ScalaFilterScope(parameters)
              Some((named, name, scope))
            case _ => None
          }
        case _ => None
      }
    }
    data match {
      case Some((named, name, scope)) =>
        val collector: SearchRequestCollector = parameters.getOptimizer
        val session: SearchSession = collector.getSearchSession
        collector.searchWord(name, scope, UsageSearchContext.IN_CODE, true, new MyProcessor(named, null, session))
      case _ =>
    }
  }

  private class MyProcessor(myTarget: PsiElement, prefix: String,
                            mySession: SearchSession) extends RequestResultProcessor(myTarget, prefix) {
    private def getAlias(element: PsiElement) = Option(element.getParent).collect {
      case selector: ScImportSelector => selector
    }.flatMap {
      _.importedName
    }

    def processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor[PsiReference]): Boolean = inReadAction {
      getAlias(element) match {
        case Some(alias) =>
          val reference: PsiReference = element.getReference
          if (reference == null) {
            return true
          }
          if (!reference.isReferenceTo(myTarget)) {
            return true
          }
          val collector: SearchRequestCollector = new SearchRequestCollector(mySession)
          val fileScope: SearchScope = new LocalSearchScope(element.getContainingFile)
          collector.searchWord(alias, fileScope, UsageSearchContext.IN_CODE, true, myTarget)
          PsiSearchHelper.SERVICE.getInstance(element.getProject).processRequests(collector, consumer)
        case _ => true
      }
    }
  }

}