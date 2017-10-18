package org.jetbrains.plugins.scala.lang
package psi
package types

import org.jetbrains.plugins.scala.project.ProjectContext

package object result {

  implicit class TypeResultExt[T](val typeResult: TypeResult[T]) extends AnyVal {

    def getOrNothing(implicit ev: T <:< ScType): ScType =
      getOrType(api.Nothing)

    def getOrAny(implicit ev: T <:< ScType): ScType =
      getOrType(api.Any)

    private def getOrType(default: ScType)
                         (implicit ev: T <:< ScType): ScType =
      if (typeResult.isEmpty) default else typeResult.get

    private implicit def context: ProjectContext = typeResult.projectContext
  }

  implicit class TypeableExt(val typeable: ScalaPsiElement with Typeable) extends AnyVal {
    def success(`type`: ScType): Success[ScType] =
      Success(`type`, Some(typeable))

    def flatMap[E <: ScalaPsiElement](maybeElement: Option[E])
                                     (function: E => TypeResult[ScType]): TypeResult[ScType] =
      maybeElement.map(function)
        .getOrElse(Failure("No element found", Some(typeable)))

    def flatMapType[E <: ScalaPsiElement with Typeable](maybeElement: Option[E]): TypeResult[ScType] =
      flatMap(maybeElement)(_.`type`())

    private implicit def context: ProjectContext = typeable
  }

}
