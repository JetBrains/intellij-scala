package org.jetbrains.plugins.scala.lang.psi
package types
package result

trait Typeable {

  import Typeable._

  /**
    * This method may be called only in a chain of typing calls between different entities of
    * typing contexts e.g. ScTypeElement, ScTypedDefinition
    *
    * @param ctx Context (possibly) augmented with informations about duplications
    */
  def getType(ctx: TypingContext.type = TypingContext): TypeResult[ScType]

}

object Typeable {

  //todo: get rid of this class; currently it is used only to resolve overloaded getType() in ScParameter
  object TypingContext

  def unapply(typeable: Typeable): Option[ScType] = Option(typeable)
    .flatMap(_.getType().toOption)
}
