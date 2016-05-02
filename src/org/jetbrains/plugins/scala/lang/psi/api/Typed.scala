package org.jetbrains.plugins.scala.lang.psi.api

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}

/**
  * @author Pavel Fatin
  */
trait Typed {
  def getType(ctx: TypingContext = TypingContext.empty): TypeResult[ScType]
}

object Typed {
  def unapply(e: Typed): Option[ScType] = e.getType() match {
    case Success(result, _) => Some(result)
    case Failure(_, _) => None
  }
}