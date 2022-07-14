package org.jetbrains.plugins.scala.lang
package psi
package types

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.{NlsString, ScalaBundle}

package object result {
  // TODO Use ScType directly. Don't use Left and Right (used to minimize the amount of changes).

  type TypeResult = ScType

  type Left[_ <: Failure, R <: ScType] = R

  object Left {
    def unapply(f: Failure): Option[Failure] = Some(f)
  }

  object Right {
    def apply(t: ScType): TypeResult = t

    def unapply(t: ScType): Option[ScType] = t match {
      case _: Failure => None
      case t => Some(t)
    }
  }

  implicit class OptionTypeExt(private val maybeRight: Option[ScType]) extends AnyVal {

    def asTypeResult(implicit context: ProjectContext): TypeResult = maybeRight match {
      case Some(result) => Right(result)
      case None => Failure(NlsString.force(""))
    }
  }

  implicit class TypeResultExt(private val result: TypeResult) extends AnyVal {
    // TODO Add the remaining "extension" methods from ScType.
  }

  implicit class TypeableExt(private val typeable: ScalaPsiElement with Typeable) extends AnyVal {

    def flatMap[E <: ScalaPsiElement](maybeElement: Option[E])
                                     (function: E => TypeResult): TypeResult =
      maybeElement.map(function)
        .getOrElse(Failure(ScalaBundle.message("no.element.found")))

    def flatMapType[E <: ScalaPsiElement with Typeable](maybeElement: Option[E]): TypeResult =
      flatMap(maybeElement)(_.`type`())

    private implicit def context: ProjectContext = typeable
  }

}
