package org.jetbrains.plugins.scala.lang.psi.types.result

import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * An entity, which may return a type and pass a context of typing while looking for others' type.
 *
 * @author ilyas
 */
trait Typeable {

  /**
   * This method may be called only in a chain of typing calls between different entities of
   * typing contexts e.g. ScTypeElement, ScTypedDefinition
   *
   * @param ctx Context (possibly) augmented with informations about duplications
   */
  def getType(ctx: TypingContext = TypingContext.empty): TypeResult[ScType]

}

object Typeable {
  def unapply(typeable: Typeable): Option[ScType] =
    Option(typeable).flatMap {
      _.getType().toOption
    }
}

//todo: get rid of this class; currently it is used only to resolve overloaded getType() in ScParameter
sealed trait TypingContext

object TypingContext {
  val empty = new TypingContext {}
}