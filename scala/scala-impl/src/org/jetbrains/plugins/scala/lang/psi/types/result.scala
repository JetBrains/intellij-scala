package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types.api.StdTypes
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.HashBuilder.toHashBuilder
import org.jetbrains.plugins.scala.{NlsString, ScalaBundle}

object result {
  trait Typeable {
    def `type`(): TypeResult
  }

  object Typeable {
    def unapply(typeable: Typeable): Option[ScType] = typeable.`type`().toOption
  }

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
      case Left(failure) if apiType != null => apiType(failure.context.stdTypes)
      case _ => throw new NoSuchElementException("Failure.get")
    }
  }

  implicit class TypeableExt(private val typeable: ScalaPsiElement with Typeable) extends AnyVal {

    def flatMap[E](maybeElement: Option[E])
                  (function: E => TypeResult): TypeResult =
      maybeElement.map(function)
        .getOrElse(Failure(ScalaBundle.message("no.element.found")))

    def flatMapType[E <: ScalaPsiElement with Typeable](maybeElement: Option[E]): TypeResult =
      flatMap(maybeElement)(_.`type`())

    private implicit def context: ProjectContext = typeable
  }

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

}
