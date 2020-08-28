package org.jetbrains.plugins.scala.dfa.lattice

import org.jetbrains.plugins.scala.dfa.lattice.JoinSemiLatticeOps.JoinSemiLatticeExt
import org.jetbrains.plugins.scala.dfa.latticeBottom

/**
 * A join-semi-lattice is a [[SemiLattice]] with a reflexive `join` operation
 * where every pair of elements has exactly one supremum.
 *
 *        X
 *      /   \
 *     /     \
 *    A       B
 *
 * Every pair of elements (A, B) have one supremum X == (A join B), which is the lowest element so that A <= X and B <= X.
 * If `A <= B` than `A join B == B`
 *
 * Because of this condition there is always a top element so that every element in the lattice <= top
 * (technically not for lattices that have an infinite height... but we are not using them here).
 *
 * @tparam L the type to implement the join-semi-lattice for
 */
trait JoinSemiLattice[L] extends SemiLattice[L] with HasTop[L] {
  def join(lhs: L, rhs: L): L

  def joinAll(first: L, others: IterableOnce[L]): L =
    others.iterator.foldLeft(first)(join)
}

trait JoinSemiLatticeOps {
  implicit final def joinSemiLatticeExt[L](element: L): JoinSemiLatticeExt[L] =
    new JoinSemiLatticeExt(element)

  final def join[L: JoinSemiLattice](first: L, others: L*): L =
    first.joinAll(others)

  final def join[L: JoinSemiLattice : HasBottom](elements: IterableOnce[L]): L = {
    val it = elements.iterator
    if (it.hasNext) it.next().joinAll(it)
    else latticeBottom
  }
}

object JoinSemiLatticeOps extends JoinSemiLatticeOps {
  final class JoinSemiLatticeExt[L](private val element: L) extends AnyVal {
    def join[LL >: L](other: LL)(implicit lattice: JoinSemiLattice[LL]): LL =
      lattice.join(element, other)

    def |[LL >: L](other: LL)(implicit lattice: JoinSemiLattice[LL]): LL =
      lattice.join(element, other)

    def joinAll[LL >: L](others: IterableOnce[LL])(implicit lattice: JoinSemiLattice[LL]): LL =
      lattice.joinAll(element, others)
  }
}