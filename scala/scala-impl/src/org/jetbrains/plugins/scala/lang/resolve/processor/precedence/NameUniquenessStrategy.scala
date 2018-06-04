package org.jetbrains.plugins.scala.lang.resolve.processor.precedence

import gnu.trove.{THashSet, TObjectHashingStrategy}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
  * Nikolay.Tropin
  * 31-May-18
  */
sealed private[processor] trait NameUniquenessStrategy extends TObjectHashingStrategy[ScalaResolveResult] {
  def isValid(result: ScalaResolveResult): Boolean = true
}

private[processor] object NameUniquenessStrategy {
  case object Implicits extends NameUniquenessStrategy {
    override def computeHashCode(t: ScalaResolveResult): Int =
      t.nameInScope.hashCode

    override def equals(t: ScalaResolveResult, t1: ScalaResolveResult): Boolean =
      t.nameInScope == t1.nameInScope
  }

  case object Completion extends NameUniquenessStrategy {
    override def computeHashCode(t: ScalaResolveResult): Int =
      t.nameInScope.hashCode + 31 * t.isNamedParameter.hashCode()

    override def equals(t: ScalaResolveResult, t1: ScalaResolveResult): Boolean =
      t.isNamedParameter == t1.isNamedParameter && t.nameInScope == t1.nameInScope
  }

  case object Resolve extends NameUniquenessStrategy {
    override def isValid(result: ScalaResolveResult): Boolean = result.qualifiedNameId != null

    override def computeHashCode(t: ScalaResolveResult): Int =
      if (t.qualifiedNameId == null) 0 else t.qualifiedNameId.hashCode

    override def equals(t: ScalaResolveResult, t1: ScalaResolveResult): Boolean =
      t.qualifiedNameId == t1.qualifiedNameId
  }
}