package org.jetbrains.plugins.scala.findUsages.typeDef

import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{PsiSearchHelper, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper

/**
 * User: Alefas
 * Date: 18.02.12
 */

class ObjectTraitReferenceSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  def execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    val scope = inReadAction(queryParameters.getEffectiveSearchScope)
    val element = queryParameters.getElementToSearch

    val toProcess = inReadAction {
      element match {
        case _ if !element.isValid => None
        case o: ScObject => Some((o, o.name))
        case wrapper@PsiClassWrapper(_: ScObject | _: ScTrait) => Some((wrapper, wrapper.getName))
        case _ => None
      }
    }
    toProcess.foreach{ case (elem, name) =>
      val processor = new TextOccurenceProcessor {
        def execute(element: PsiElement, offsetInElement: Int): Boolean = {
          val references = inReadAction(element.getReferences)
          for (ref <- references if ref.getRangeInElement.contains(offsetInElement)) {
            inReadAction {
              if (ref.isReferenceTo(elem) || ref.resolve() == elem) {
                if (!consumer.process(ref)) return false
              }
            }
          }
          true
        }
      }
      val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(queryParameters.getProject)
      try {
        helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
      }
      catch {
        case _: IndexNotReadyException =>
        case ignore: AssertionError if ignore.getMessage endsWith "has null range" =>
      }
    }
    true
  }
}
