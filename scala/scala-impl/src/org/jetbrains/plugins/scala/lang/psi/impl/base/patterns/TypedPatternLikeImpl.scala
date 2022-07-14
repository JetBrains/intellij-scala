package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result._

trait TypedPatternLikeImpl extends ScPattern { this: Typeable =>

  final override def isIrrefutableFor(t: Option[ScType]): Boolean = {
    for {
      ty <- t
      Right(tp) <- Some(`type`())
    } yield {
      ty conforms tp
    }
  }.getOrElse(false)
}
