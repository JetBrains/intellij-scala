package org.jetbrains.plugins.scala
package findUsages.apply

import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch.SearchParameters
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.extensions.{Both, ContainingClass, inReadAction}
import org.jetbrains.plugins.scala.finder.ScalaFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * Nikolay.Tropin
 * 8/29/13
 */
abstract class ApplyUnapplyMethodSearcherBase extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {

  protected val names: Set[String]

  protected def checkAndTransform(ref: PsiReference): Option[ScReferenceElement]

  def execute(queryParameters: SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    val element = queryParameters.getElementToSearch
    val ignoreAccess = queryParameters.isIgnoreAccessScope

    val data = inReadAction {
      element match {
        case Both(fun: ScFunctionDefinition, ContainingClass(obj: ScObject)) if names.contains(fun.name) =>
          val scope = ScalaFilterScope(queryParameters)
          Some((fun, obj, scope))
        case _ => None
      }
    }
    data match {
      case Some((fun, obj, scope)) =>
        val processor = new Processor[PsiReference] {
          def process(ref: PsiReference): Boolean = {
            inReadAction {
              checkAndTransform(ref).flatMap(_.bind()) match {
                case Some(ScalaResolveResult(`fun`, _)) => consumer.process(ref)
                case _ => true
              }
            }
          }
        }
        ReferencesSearch.search(obj, scope, ignoreAccess).forEach(processor)
      case _ => true
    }
  }
}
