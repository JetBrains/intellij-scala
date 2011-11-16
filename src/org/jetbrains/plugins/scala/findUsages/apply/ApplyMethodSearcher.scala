package org.jetbrains.plugins.scala
package findUsages
package apply

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.{Processor, QueryExecutor}
import com.intellij.psi._
import lang.psi.api.statements.ScFunctionDefinition
import lang.psi.api.toplevel.typedef.ScObject
import lang.psi.api.expr.ScMethodCall
import lang.resolve.{ScalaResolveResult, ResolvableReferenceExpression}
import org.jetbrains.plugins.scala.util.ScalaUtil


class ApplyMethodSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  def execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    val scope = queryParameters.getScope
    val element = queryParameters.getElementToSearch
    val ignoreAccess = queryParameters.isIgnoreAccessScope
    ScalaUtil.readAction(element.getProject) {
      element match {
        case fun: ScFunctionDefinition if fun.name == "apply" =>
          val processor = new Processor[PsiReference] {
            def process(ref: PsiReference): Boolean = {
              (ref, ref.getElement.getContext) match {
                case (sref: ResolvableReferenceExpression, x: ScMethodCall) if x.getInvokedExpr == ref.getElement =>
                  sref.bind() match {
                    case Some(ScalaResolveResult(`fun`, _)) => consumer.process(ref)
                    case _ => true
                  }
                case _ => true
              }
            }
          }
          fun.containingClass match {
            case Some(obj: ScObject) => ReferencesSearch.search(obj, scope, ignoreAccess).forEach(processor)
            case _ =>
          }
        // TODO Check every ScMethodCall? Sounds expensive!
        case _ =>
      }
    }
    true
  }
}
