package org.jetbrains.plugins.scala
package findUsages.vals

import com.intellij.openapi.project.{IndexNotReadyException, Project}
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch.SearchParameters
import com.intellij.psi.search.{PsiSearchHelper, SearchScope, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.finder.ScalaFilterScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScConstructorPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScNewTemplateDefinition, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

class ApplyUnapplyForBindingSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  override def execute(queryParameters: SearchParameters, consumer: Processor[_ >: PsiReference]): Boolean = {
    val project = queryParameters.getProject
    val scope = inReadAction {
      ScalaFilterScope(queryParameters)
    }
    val element = queryParameters.getElementToSearch
    element match {
      case _ if inReadAction(!element.isValid) => true
      case binding: ScBindingPattern =>
        val processor = createProcessor(consumer, binding, checkApply = true, checkUnapply = true)
        processBinding(processor, scope, binding, project)
      case inAnonClassWithBinding((binding, checkApply, checkUnapply)) =>
        val processor = createProcessor(consumer, binding, checkApply, checkUnapply)
        processBinding(processor, scope, binding, project)
      case _ => true
    }
  }


  private def createProcessor(consumer: Processor[_ >: PsiReference], binding: ScBindingPattern, checkApply: Boolean, checkUnapply: Boolean): TextOccurenceProcessor =
    new TextOccurenceProcessor {
      override def execute(element: PsiElement, offsetInElement: Int): Boolean = {
        val references = inReadAction(element.getReferences)
        val IsApply = new Apply(binding)
        val IsUnapply = new Unapply(binding)
        for (ref <- references if ref.getRangeInElement.contains(offsetInElement)) {
          inReadAction {
            ref match {
              case IsApply(reference) if checkApply => if (!consumer.process(reference)) return false
              case IsUnapply(reference) if checkUnapply => if (!consumer.process(reference)) return false
              case _ =>
            }
          }
        }
        true
      }
    }

  private def processBinding(processor: TextOccurenceProcessor, scope: SearchScope, binding: ScBindingPattern, project: Project): Boolean = {
    val helper: PsiSearchHelper = PsiSearchHelper.getInstance(project)
    try {
      val name = inReadAction(binding.name)
      helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
    }
    catch {
      case _: IndexNotReadyException => true
    }
  }

  private class Unapply(binding: ScBindingPattern) {
    def unapply(ref: PsiReference): Option[ScReference] = {
      (ref, ref.getElement.getContext) match {
        case (sref: ScStableCodeReference, _: ScConstructorPattern) =>
          sref.bind() match {
            case Some(resolve@ScalaResolveResult(fun: ScFunctionDefinition, _))
              if Set("unapply", "unapplySeq").contains(fun.name) =>
              resolve.innerResolveResult match {
                case Some(ScalaResolveResult(`binding`, _)) => Some(sref)
                case _ => None
              }
            case Some(resolve@ScalaResolveResult(`binding`, _)) =>
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
    def unapply(ref: PsiReference): Option[ScReference] = {
      (ref, ref.getElement.getContext) match {
        case (sref: ScReferenceExpression, x: ScMethodCall) if x.getInvokedExpr == ref.getElement =>
          sref.bind() match {
            case Some(ScalaResolveResult(fun: ScFunctionDefinition, _))
              if fun.name == "apply" && sref.isReferenceTo(binding) => Some(sref)
            case Some(resolve@ScalaResolveResult(`binding`, _)) =>
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

  private object inAnonClassWithBinding {
    def unapply(fun: ScFunctionDefinition): Option[(ScBindingPattern, Boolean, Boolean)] =
      inReadAction {
        val (checkApply, checkUnapply) = fun.name match {
          case "apply" => (true, false)
          case "unapply" | "unapplySeq" => (false, true)
          case _ => (false, false)
        }
        if (checkApply || checkUnapply) {
          fun.containingClass match {
            case anon: ScNewTemplateDefinition => ScalaPsiUtil.findInstanceBinding(anon).flatMap(Some(_, checkApply, checkUnapply))
            case _ => None
          }
        }
        else None
      }
  }

}

