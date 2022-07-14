package org.jetbrains.plugins.scala.lang.psi
package types
package result

import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.plugins.scala.lang.psi.types.api.ValueType
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.HashBuilder._

final class Failure(val cause: NlsString)
                   (implicit val context: ProjectContext) extends ValueType with LeafType {

  // TODO Implement conformance, equivalence, etc.

  override def visitType(visitor: ScalaTypeVisitor): Unit = {}

  override implicit def projectContext: ProjectContext = context

  override def equals(other: Any): Boolean = other match {
    case that: Failure => cause == that.cause && context == that.context
    case _ => false
  }

  override def hashCode(): Int = cause.## #+ context
}

object Failure {

  def apply(@Nls cause: String)
           (implicit context: ProjectContext): ScType =
    new Failure(NlsString(cause))

  def unapply(result: Failure): Some[NlsString] =
    Some(result.cause)
}
