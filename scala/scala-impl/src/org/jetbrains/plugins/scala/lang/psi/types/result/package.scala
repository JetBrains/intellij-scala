package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types.api.StdTypes
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectExt}
import org.jetbrains.plugins.scala.{NlsString, ScalaBundle}

package object result {

  import scala.util.{Either, Left, Right}

  type TypeResult = Either[Failure, ScType]

  implicit class OptionTypeExt(private val maybeRight: Option[ScType]) extends AnyVal {

    def asTypeResult(implicit context: ProjectContext): TypeResult = maybeRight match {
      case Some(result) => Right(result)
      case None => Failure(NlsString.force(""))
    }
  }

  implicit class TypeResultExt(private val result: TypeResult) extends AnyVal {

    def get: ScType = getOrApiType(null)

    def getOrAny: ScType = getOrApiType(_.Any)

    def getOrNothing: ScType = getOrApiType(_.Nothing)

    private def getOrApiType(apiType: StdTypes => ScType): ScType = result match {
      case Right(value) => value
      case Left(failure) if apiType != null => apiType(failure.context.project.stdTypes)
      case _ => throw new NoSuchElementException("Failure.get")
    }
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
