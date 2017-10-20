package org.jetbrains.plugins.scala.lang
package psi
package types

import org.jetbrains.plugins.scala.project.ProjectContext

package object result {

  implicit class TypeResultExt(val typeResult: TypeResult[ScType]) extends AnyVal {

    def getOrNothing: ScType = typeResult.getOrElse(api.Nothing)

    def getOrAny: ScType = typeResult.getOrElse(api.Any)

    private implicit def context: ProjectContext = typeResult.projectContext
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
