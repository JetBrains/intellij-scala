package org.jetbrains.plugins.scala.lang
package psi
package types

import org.jetbrains.plugins.scala.project.ProjectContext

package object result {

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
