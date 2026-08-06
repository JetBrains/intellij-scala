package org.jetbrains.plugins.scala.findUsages.typeDef

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{LocalSearchScope, PsiSearchHelper, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScSelfInvocation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScConstructorOwner, ScTemplateDefinition}

class SelfInvocationSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  override def execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[_ >: PsiReference]): Boolean = {
    queryParameters.getElementToSearch match {
      case ml: ScMethodLike if inReadAction(ml.isConstructor) =>
        doExecute(ml, inReadAction(Option(ml.containingClass)))(queryParameters, consumer)
      case td: ScConstructorOwner =>
        inReadAction(td.constructor) match {
          case Some(ml) =>
            doExecute(ml, Some(td))(queryParameters, consumer)
          case _ => true
        }
      case _ => true
    }
  }

  private def doExecute(ml: ScMethodLike, containingClass: Option[ScTemplateDefinition])
                 (queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[_ >: PsiReference]): Boolean = {
    val localScope = inReadAction {
      containingClass.map {
        new LocalSearchScope(_)
          .intersectWith(queryParameters.getEffectiveSearchScope)
      }
    }

    localScope.forall { scope =>
      val helper: PsiSearchHelper = PsiSearchHelper.getInstance(queryParameters.getProject)
      val processor = new TextOccurenceProcessor {
        override def execute(element: PsiElement, offsetInElement: Int): Boolean = {
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
  }
}
