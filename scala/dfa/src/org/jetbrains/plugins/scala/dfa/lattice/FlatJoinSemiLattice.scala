package org.jetbrains.plugins.scala.dfa.lattice

import org.jetbrains.plugins.scala.dfa.JoinSemiLattice

final class FlatJoinSemiLattice[T](override val top: T)
  extends JoinSemiLattice[T]
{
  override def <=(subSet: T, superSet: T): Boolean = (subSet, superSet) match {
    case (a, b) if a == b => true
    case (_, `top`) => true
    case _ => false
  }

  override def intersects(lhs: T, rhs: T): Boolean = (lhs, rhs) match {
    case (a, b) if a == b => true
    case (`top`, _) => true
    case (_, `top`) => true
    case _ => false
  }

  override def join(lhs: T, rhs: T): T =
    if (lhs == rhs) lhs else top

  override def joinAll(first: T, others: TraversableOnce[T]): T = {
    if (first == top) top
    else {
      val allTheSame = others.forall(_ == first)
      if (allTheSame) first
      else top
    }
  }
}
