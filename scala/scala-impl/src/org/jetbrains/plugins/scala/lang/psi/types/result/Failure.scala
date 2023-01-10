package org.jetbrains.plugins.scala.lang.psi.types.result

import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.HashBuilder._

final class Failure(private[result] val cause: NlsString)
                   (private[result] implicit val context: ProjectContext) {

  override def toString = s"Failure($cause)"

  override def equals(other: Any): Boolean = other match {
    case that: Failure => cause == that.cause && context == that.context
    case _ => false
  }

  override def hashCode(): Int = cause.## #+ context
}

object Failure {

  def apply(@Nls cause: String)
           (implicit context: ProjectContext): TypeResult =
    Left(new Failure(NlsString(cause)))

  def unapply(result: Left[Failure, ScType]): Some[NlsString] =
    Some(result.value.cause)
}
