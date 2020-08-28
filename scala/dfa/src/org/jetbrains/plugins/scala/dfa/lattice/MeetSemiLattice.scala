package org.jetbrains.plugins.scala.dfa.lattice

import org.jetbrains.plugins.scala.dfa.lattice.MeetSemiLatticeOps.MeetSemiLatticeExt
import org.jetbrains.plugins.scala.dfa.latticeTop

import scala.language.implicitConversions

/**
 * A meet-semi-lattice is a [[SemiLattice]] with a reflexive `meet` operation
 * where every pair of elements has exactly one infimum.
 *
 *    A      B
 *     \    /
 *      \  /
 *       Y
 *
 * Every pair of elements (A, B) have one infimum Y == (A meet B), which is the greatest element so that Y <= A and Y <= B.
 * If `A <= B` than `A meet B == A`
 *
 * Because of this condition there is always a bottom element so that every element in the lattice >= bottom
 * (technically not for lattices that have an infinite height... but we are not using them here).
 *
 * @tparam L the type to implement the join-semi-lattice for
 */
trait MeetSemiLattice[L] extends SemiLattice[L] with HasBottom[L] {
  def meet(lhs: L, rhs: L): L

  def meetAll(first: L, others: IterableOnce[L]): L =
    others.iterator.foldLeft(first)(meet)
}

trait MeetSemiLatticeOps {
  implicit final def meetSemiLatticeExt[L](element: L): MeetSemiLatticeExt[L] =
    new MeetSemiLatticeExt(element)

  final def meet[L: MeetSemiLattice](first: L, others: L*): L =
    first.meetAll(others)

  final def meet[L: MeetSemiLattice : HasTop](elements: IterableOnce[L]): L = {
    val it = elements.iterator
    if (it.hasNext) it.next().meetAll(it)
    else latticeTop
  }
}

object MeetSemiLatticeOps extends MeetSemiLatticeOps {
  final class MeetSemiLatticeExt[L](private val element: L) extends AnyVal {
    def meet[LL >: L](other: LL)(implicit lattice: MeetSemiLattice[LL]): LL =
      lattice.meet(element, other)

    def &[LL >: L](other: LL)(implicit lattice: MeetSemiLattice[LL]): LL =
      lattice.meet(element, other)

    def meetAll[LL >: L](others: IterableOnce[LL])(implicit lattice: MeetSemiLattice[LL]): LL =
      lattice.meetAll(element, others)
  }
}
