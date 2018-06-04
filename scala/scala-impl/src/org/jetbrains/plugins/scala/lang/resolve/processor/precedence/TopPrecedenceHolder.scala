package org.jetbrains.plugins.scala.lang
package resolve
package processor
package precedence

import gnu.trove.{TObjectHashingStrategy, TObjectIntHashMap}

trait TopPrecedenceHolder {

  /**
    * Returns highest precedence of all resolve results.
    * 1 - import a._
    * 2 - import a.x
    * 3 - definition or declaration
    */
  def apply(result: ScalaResolveResult): Int

  def update(result: ScalaResolveResult, i: Int): Unit

  def filterNot(left: ScalaResolveResult,
                right: ScalaResolveResult)
               (precedence: ScalaResolveResult => Int): Boolean =
    precedence(left) < apply(right)
}

class MappedTopPrecedenceHolder(strategy: TObjectHashingStrategy[ScalaResolveResult]) extends TopPrecedenceHolder {

  private[this] val precedences = new TObjectIntHashMap[ScalaResolveResult](strategy)

  override def apply(result: ScalaResolveResult): Int =
    precedences.get(result)

  override def update(result: ScalaResolveResult, i: Int): Unit = {
    precedences.put(result, i)
  }

  override def filterNot(left: ScalaResolveResult,
                         right: ScalaResolveResult)
                        (precedence: ScalaResolveResult => Int): Boolean =
    strategy.equals(left, right) &&
      super.filterNot(left, right)(precedence)
}

class SimpleTopPrecedenceHolder extends TopPrecedenceHolder {

  private[this] var precedence: Int = 0

  override def apply(result: ScalaResolveResult): Int = precedence

  override def update(result: ScalaResolveResult, i: Int): Unit = {
    precedence = i
  }

  def reset(): Unit = {
    precedence = 0
  }

  def currentPrecedence: Int = precedence
}
