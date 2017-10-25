package org.jetbrains.plugins.scala.lang
package psi
package types

import org.jetbrains.plugins.scala.lang.psi.types.api.StdTypes
import org.jetbrains.plugins.scala.project.ProjectContext

package object result {

  type TypeError = (String, ProjectContext)
  type TypeResult[T <: ScType] = Either[TypeError, ScType]

  object Success {
    def apply(result: ScType): TypeResult[ScType] = Right(result)

    def unapply(success: Right[TypeError, ScType]): Option[ScType] = Some(success.value)
  }

  object Failure {
    def apply(cause: String)
             (implicit context: ProjectContext): TypeResult[ScType] = Left(cause, context)

    def unapply(left: Left[TypeError, ScType]): Option[String] = {
      val (cause, _) = left.value
      Some(cause)
    }
  }

  object TypeResult {
    def apply(maybeType: Option[ScType])
             (implicit context: ProjectContext): TypeResult[ScType] = maybeType match {
      case Some(scType) => Success(scType)
      case None => Failure("")
    }
  }

  implicit class TypeResultExt(val result: TypeResult[ScType]) extends AnyVal {

    def get: ScType = result match {
      case Right(value) => value
      case _ => throw new NoSuchElementException("Failure.get")
    }

    def isEmpty: Boolean = result.isLeft

    def isDefined: Boolean = result.isRight

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
