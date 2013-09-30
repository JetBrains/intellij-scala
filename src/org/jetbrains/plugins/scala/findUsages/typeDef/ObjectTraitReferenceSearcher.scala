package org.jetbrains.plugins.scala.findUsages.typeDef

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.{Processor, QueryExecutor}
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.psi.search.{UsageSearchContext, PsiSearchHelper, TextOccurenceProcessor}
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTrait, ScObject}
import com.intellij.openapi.project.IndexNotReadyException
import org.jetbrains.plugins.scala.extensions

/**
 * User: Alefas
 * Date: 18.02.12
 */

class ObjectTraitReferenceSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  def execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    extensions.inReadAction {
      val scope = queryParameters.getEffectiveSearchScope
      val element = queryParameters.getElementToSearch
      if (!element.isValid) return true
      val toProcess = element match {
        case o: ScObject => Some((o, o.name))
        case wrapper: PsiClassWrapper  =>
          wrapper.definition match {
            case _: ScObject | _: ScTrait => Some((wrapper, wrapper.getName))
            case _ => None
          }
        case _ => None
      }
      toProcess.foreach{ case (elem, name) =>
        val processor = new TextOccurenceProcessor {
          def execute(element: PsiElement, offsetInElement: Int): Boolean = {
            val references = element.getReferences
            for (ref <- references if ref.getRangeInElement.contains(offsetInElement)) {
              if (ref.isReferenceTo(elem) || ref.resolve() == elem) {
                if (!consumer.process(ref)) return false
              }
            }
            true
          }
        }
        val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(elem.getProject)
        try {
          helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
        }
        catch {
          case ignore: IndexNotReadyException =>
          case ignore: AssertionError if ignore.getMessage endsWith "has null range" =>
        }
      }
      true
    }
  }
}