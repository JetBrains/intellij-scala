package org.jetbrains.plugins.scala
package findUsages.apply

import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch.SearchParameters
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.extensions.{&&, ContainingClass, inReadAction}
import org.jetbrains.plugins.scala.finder.ScalaFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

abstract class ApplyUnapplyMethodSearcherBase extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {

  protected val names: Set[String]

  protected def checkAndTransform(ref: PsiReference): Option[ScReference]

  override def execute(queryParameters: SearchParameters, consumer: Processor[_ >: PsiReference]): Boolean = {
    val element = queryParameters.getElementToSearch
    val ignoreAccess = queryParameters.isIgnoreAccessScope

    val data = inReadAction {
      element match {
        case (fun: ScFunctionDefinition) && ContainingClass(obj: ScObject) if names.contains(fun.name) =>
          val scope = ScalaFilterScope(queryParameters)
          Some((fun, obj, scope))
        case _ => None
      }
    }
    data match {
      case Some((fun, obj, scope)) =>
        val processor = new Processor[PsiReference] {
          override def process(ref: PsiReference): Boolean = {
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
