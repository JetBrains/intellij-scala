package org.jetbrains.plugins.scala.findUsages.typeDef

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.{Processor, QueryExecutor}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.psi.search.{UsageSearchContext, PsiSearchHelper, TextOccurenceProcessor}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTrait, ScObject}

/**
 * User: Alefas
 * Date: 18.02.12
 */

class ObjectTraitReferenceSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  def execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    val scope = queryParameters.getScope
    val element = queryParameters.getElementToSearch
    ApplicationManager.getApplication.runReadAction(new Computable[Boolean] {
      def compute: Boolean = {
        element match {
          case o: ScObject => {
            val name: String = o.name
            val processor = new TextOccurenceProcessor {
              def execute(element: PsiElement, offsetInElement: Int): Boolean = {
                val references = element.getReferences
                for (ref <- references if ref.getRangeInElement.contains(offsetInElement)) {
                  ref match {
                    case refElement: ScReferenceElement =>
                      if (refElement.isReferenceTo(o)) {
                        if (!consumer.process(ref)) return false
                      }
                    case _ =>
                  }
                }
                true
              }
            }
            val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(o.getProject)
            helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
          }
          case wrapper: PsiClassWrapper if wrapper.definition.isInstanceOf[ScObject] => {
            val name: String = wrapper.getName
            val processor = new TextOccurenceProcessor {
              def execute(element: PsiElement, offsetInElement: Int): Boolean = {
                val references = element.getReferences
                for (ref <- references if ref.getRangeInElement.contains(offsetInElement)) {
                  if (ref.isReferenceTo(wrapper)) {
                    if (!consumer.process(ref)) return false
                  }
                }
                true
              }
            }
            val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(wrapper.getProject)
            helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
          }
          case wrapper: PsiClassWrapper if wrapper.definition.isInstanceOf[ScTrait] => {
            val name: String = wrapper.getName
            val processor = new TextOccurenceProcessor {
              def execute(element: PsiElement, offsetInElement: Int): Boolean = {
                val references = element.getReferences
                for (ref <- references if ref.getRangeInElement.contains(offsetInElement)) {
                  if (ref.isReferenceTo(wrapper)) {
                    if (!consumer.process(ref)) return false
                  }
                }
                true
              }
            }
            val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(wrapper.getProject)
            helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
          }
          case _ =>
        }
        true
      }
    })
  }
}