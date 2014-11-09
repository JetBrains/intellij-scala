package org.jetbrains.plugins.scala.lang.psi.types.result

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType

import scala.collection.immutable.HashSet

/**
 * An entity, which may return a type and pass a context of typing while looking for others' type.
 *
 * @author ilyas
 */
trait TypingContextOwner {

  /**
   * This method may be called only in a chain of typing calls between different entities of
   * typing contexts e.g. ScTypeElement, ScTypedDefinition
   *
   * @param ctx Context (possibly) augmented with informations about duplications
   */
  def getType(ctx: TypingContext = TypingContext.empty): TypeResult[ScType]

}

trait TypingContext {
  self =>

  /**
   * Set of visited elements to prevent cycles
   */
  def visited: Set[ScNamedElement]

  def isUndefined = false

  def apply(named: ScNamedElement): TypingContext = new TypingContext {
    def visited = HashSet(self.visited.toSeq: _*) + named
  }

  def apply(seq: Seq[ScNamedElement]): TypingContext = seq.foldLeft(TypingContext.empty)((ctx, elem) => ctx(elem))

}

object TypingContext {
  val empty = new TypingContext {def visited = Set()}

  val undefined = new TypingContext {
    def visited = Set()
    override def isUndefined = true
  }

}