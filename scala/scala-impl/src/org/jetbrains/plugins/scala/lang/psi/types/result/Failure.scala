package org.jetbrains.plugins.scala.lang.psi
package types
package result

import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.HashBuilder._

final class Failure(private[result] val cause: String)
                   (private[result] implicit val context: ProjectContext) {

  override def toString = s"Failure($cause)"

  override def equals(other: Any): Boolean = other match {
    case that: Failure => cause == that.cause && context == that.context
    case _ => false
  }

  override def hashCode(): Int = cause #+ context
}

object Failure {

  import scala.util.{Either, Left}

  def apply(cause: String)
           (implicit context: ProjectContext): Left[Failure, ScType] =
    Left(new Failure(cause))

  def unapply(result: Either[Failure, ScType]): Option[String] = result match {
    case Left(failure) => Some(failure.cause)
    case _ => None
  }
}
