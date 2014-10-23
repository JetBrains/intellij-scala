package org.jetbrains.plugins.scala
package findUsages.vals

import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch.SearchParameters
import com.intellij.psi.search.{PsiSearchHelper, SearchScope, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScConstructorPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.resolve.{ResolvableReferenceElement, ResolvableReferenceExpression, ScalaResolveResult}

/**
 * Nikolay.Tropin
 * 8/29/13
 */
class ApplyUnapplyForBindingSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  def execute(queryParameters: SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    extensions.inReadAction {
      val scope = queryParameters.getEffectiveSearchScope
      val element = queryParameters.getElementToSearch
      if (!element.isValid) return true
      element match {
        case binding: ScBindingPattern =>
          val processor = createProcessor(consumer, binding, checkApply = true, checkUnapply = true)
          processBinding(processor, scope, binding)

        //for bindings to anonimous classes
        case fun: ScFunctionDefinition =>
          val (checkApply, checkUnapply) = fun.name match {
            case "apply" => (true, false)
            case "unapply" | "unapplySeq" => (false, true)
            case _ => (false, false)
          }
          if (checkApply || checkUnapply) {
            fun.containingClass match {
              case anon: ScNewTemplateDefinition =>
                val bindingOpt = ScalaPsiUtil.findInstanceBinding(anon)
                val binding = bindingOpt.getOrElse(return true)
                val processor = createProcessor(consumer, binding, checkApply, checkUnapply)
                processBinding(processor, scope, binding)
              case _ =>
            }
          }
        case _ =>
      }
    }
    true
  }


  private def createProcessor(consumer: Processor[PsiReference], binding: ScBindingPattern, checkApply: Boolean, checkUnapply: Boolean) =
    new TextOccurenceProcessor {
      def execute(element: PsiElement, offsetInElement: Int): Boolean = {
        val references = element.getReferences
        val IsApply = new Apply(binding)
        val IsUnapply = new Unapply(binding)
        for (ref <- references if ref.getRangeInElement.contains(offsetInElement)) {
          ref match {
            case IsApply(reference) if checkApply => if (!consumer.process(reference)) return false
            case IsUnapply(reference) if checkUnapply => if (!consumer.process(reference)) return false
            case _ =>
          }
        }
        true
      }
    }

  private def processBinding(processor: TextOccurenceProcessor, scope: SearchScope, binding: ScBindingPattern): Boolean = {
    val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(binding.getProject)
    try {
      helper.processElementsWithWord(processor, scope, binding.name, UsageSearchContext.IN_CODE, true)
    }
    catch {
      case ignore: IndexNotReadyException => true
    }
  }

  private class Unapply(binding: ScBindingPattern) {
    def unapply(ref: PsiReference): Option[ResolvableReferenceElement] = {
      (ref, ref.getElement.getContext) match {
        case (sref: ScStableCodeReferenceElement, x: ScConstructorPattern) =>
          sref.bind() match {
            case Some(resolve @ ScalaResolveResult(fun: ScFunctionDefinition, _))
              if Set("unapply", "unapplySeq").contains(fun.name) =>
              resolve.innerResolveResult match {
                case Some(ScalaResolveResult(`binding`, _)) => Some(sref)
                case _ => None
              }
            case Some(resolve @ ScalaResolveResult(`binding`, _)) =>
              resolve.innerResolveResult match {
                case Some(ScalaResolveResult(fun: ScFunctionDefinition, _))
                  if Set("unapply", "unapplySeq").contains(fun.name) => Some(sref)
                case _ => None
              }
            case _ => None
          }
        case _ => None
      }
    }
  }

  private class Apply(binding: ScBindingPattern) {
    def unapply(ref: PsiReference): Option[ResolvableReferenceElement] = {
      (ref, ref.getElement.getContext) match {
        case (sref: ResolvableReferenceExpression, x: ScMethodCall) if x.getInvokedExpr == ref.getElement =>
          sref.bind() match {
            case Some(ScalaResolveResult(fun: ScFunctionDefinition, _))
              if fun.name == "apply" && sref.isReferenceTo(binding) => Some(sref)
            case Some(resolve @ ScalaResolveResult(`binding`, _)) =>
              resolve.innerResolveResult match {
                case Some(ScalaResolveResult(fun: ScFunctionDefinition, _)) if fun.name == "apply" => Some(sref)
                case _ => None
              }
            case _ => None
          }
        case _ => None
      }
    }
  }

}

