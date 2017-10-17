package org.jetbrains.plugins.scala.lang
package psi
package types

import org.jetbrains.plugins.scala.project.ProjectContext

package object result {

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
