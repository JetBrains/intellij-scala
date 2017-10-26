package org.jetbrains.plugins.scala.lang
package psi
package types

import org.jetbrains.plugins.scala.lang.psi.types.api.StdTypes
import org.jetbrains.plugins.scala.project.ProjectContext

package object result {

  private type TypeError = (String, ProjectContext)
  type TypeResult[T <: ScType] = Either[TypeError, ScType]

  object Failure {
    def apply(cause: String)
             (implicit context: ProjectContext): TypeResult[ScType] = Left(cause, context)

    def unapply(result: TypeResult[ScType]): Option[String] = result match {
      case Left((cause, _)) => Some(cause)
      case _ => None
    }
  }

  object TypeResult {
    def apply(maybeType: Option[ScType])
             (implicit context: ProjectContext): TypeResult[ScType] = maybeType match {
      case Some(result) => Right(result)
      case None => Failure("")
    }
  }

  implicit class TypeResultExt(val result: TypeResult[ScType]) extends AnyVal {

    def get: ScType = result match {
      case Right(value) => value
      case _ => throw new NoSuchElementException("Failure.get")
    }

    def getOrAny: ScType = getOrApiType(_.Any)

    def getOrNothing: ScType = getOrApiType(_.Nothing)

    private def getOrApiType(apiType: StdTypes => ScType): ScType = result match {
      case Right(value) => value
      case Left((_, projectContext)) => apiType(projectContext.stdTypes)
    }
  }

  implicit class TypeableExt(val typeable: ScalaPsiElement with Typeable) extends AnyVal {

    def flatMap[E <: ScalaPsiElement](maybeElement: Option[E])
                                     (function: E => TypeResult[ScType]): TypeResult[ScType] =
      maybeElement.map(function)
        .getOrElse(Failure("No element found"))

    def flatMapType[E <: ScalaPsiElement with Typeable](maybeElement: Option[E]): TypeResult[ScType] =
      flatMap(maybeElement)(_.`type`())

    private implicit def context: ProjectContext = typeable
  }

}
