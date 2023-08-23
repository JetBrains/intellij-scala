package org.jetbrains.plugins.scala.lang.resolve.processor.precedence

import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

sealed trait TopPrecedenceHolder {

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

final class MappedTopPrecedenceHolder(strategy: Hash.Strategy[ScalaResolveResult]) extends TopPrecedenceHolder {

  private[this] val precedences = new Object2IntOpenCustomHashMap[ScalaResolveResult](strategy)

  override def apply(result: ScalaResolveResult): Int =
    precedences(result)

  override def update(result: ScalaResolveResult, i: Int): Unit = {
    precedences.put(result, i)
  }

  override def filterNot(left: ScalaResolveResult,
                         right: ScalaResolveResult)
                        (precedence: ScalaResolveResult => Int): Boolean =
    strategy.equals(left, right) &&
      super.filterNot(left, right)(precedence)
}

final class SimpleTopPrecedenceHolder extends TopPrecedenceHolder {

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
