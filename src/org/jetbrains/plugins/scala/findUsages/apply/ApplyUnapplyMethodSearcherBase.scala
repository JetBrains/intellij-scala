package org.jetbrains.plugins.scala
package findUsages.apply

import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch.SearchParameters
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.resolve.{ResolvableReferenceElement, ScalaResolveResult}

/**
 * Nikolay.Tropin
 * 8/29/13
 */
abstract class ApplyUnapplyMethodSearcherBase extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {

  protected val names: Set[String]

  protected def checkAndTransform(ref: PsiReference): Option[ResolvableReferenceElement]

  def execute(queryParameters: SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    val scope = inReadAction(queryParameters.getEffectiveSearchScope)
    val element = queryParameters.getElementToSearch
    val ignoreAccess = queryParameters.isIgnoreAccessScope
    element match {
      case fun: ScFunctionDefinition if names.contains(fun.name) =>
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
        inReadAction(fun.containingClass) match {
          case obj: ScObject => ReferencesSearch.search(obj, scope, ignoreAccess).forEach(processor)
          case _ => true
        }
      case _ => true
    }
  }
}
