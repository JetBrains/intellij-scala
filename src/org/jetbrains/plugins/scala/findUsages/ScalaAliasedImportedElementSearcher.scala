package org.jetbrains.plugins.scala
package findUsages

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.search._
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector


class ScalaAliasedImportedElementSearcher extends QueryExecutorBase[PsiReference, ReferencesSearch.SearchParameters](true) {

  def processQuery(parameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]) {
    val target: Option[PsiNamedElement] = inReadAction {
      parameters.getElementToSearch match {
        case named: PsiNamedElement =>
          ScalaPsiUtil.nameContext(named) match {
            case _: PsiNamedElement | _: PsiMember | _: ScTypeAlias => Some(named)
            case _ => None
          }
        case _ => None
      }
    }
    for {
      named <- target
      name <- Option(named.name)
      if !StringUtil.isEmptyOrSpaces(name)
    } {
      val scope: SearchScope = inReadAction(parameters.getEffectiveSearchScope) // TODO PsiUtil.restrictScopeToGroovyFiles(parameters.getEffectiveSearchScope)
      val collector: SearchRequestCollector = parameters.getOptimizer
      val session: SearchSession = collector.getSearchSession
      collector.searchWord(name, scope, UsageSearchContext.IN_CODE, true, new MyProcessor(named, null, session))
    }
  }

  private class MyProcessor(myTarget: PsiElement, prefix: String,
                            mySession: SearchSession) extends RequestResultProcessor(myTarget, prefix) {
    private def getAlias(element: PsiElement): String = {
        if (!element.getParent.isInstanceOf[ScImportSelector]) return null
      val importStatement: ScImportSelector = element.getParent.asInstanceOf[ScImportSelector]
      importStatement.importedName
    }

    def processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor[PsiReference]): Boolean = inReadAction {
      val alias: String = getAlias(element)
      if (alias == null) return true
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
    }
  }

}