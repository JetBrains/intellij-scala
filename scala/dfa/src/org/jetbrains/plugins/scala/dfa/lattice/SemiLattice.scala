package org.jetbrains.plugins.scala.dfa.lattice

import org.jetbrains.plugins.scala.dfa.lattice.SemiLatticeOps.SemiLatticeExt

/**
 * Type class to make [[L]] a semi-lattice
 *
 * A [[SemiLattice]] is an algebraic structure with a set of elements that are partially ordered.
 *
 * @see [[JoinSemiLattice]] and [[MeetSemiLattice]] for more operations.
 * @tparam L the type to implement the semi-lattice for
 */
trait SemiLattice[-L] {
  def <=(subSet: L, superSet: L): Boolean
  def <(subSet: L, superSet: L): Boolean = this.<=(subSet, superSet) && subSet != superSet
  def intersects(lhs: L, rhs: L): Boolean
}

trait SemiLatticeOps {
  implicit final def semiLatticeExt[L](element: L): SemiLatticeExt[L] =
    new SemiLatticeExt(element)
}

object SemiLatticeOps extends SemiLatticeOps {
  final class SemiLatticeExt[L](private val element: L) extends AnyVal {
    def <=[LL >: L](other: LL)(implicit lattice: SemiLattice[LL]): Boolean =
      lattice.<=(element, other)

    def >=[LL >: L](other: LL)(implicit lattice: SemiLattice[LL]): Boolean =
      lattice.<=(other, element)

    def <[LL >: L](other: LL)(implicit lattice: SemiLattice[LL]): Boolean =
      lattice.<(element, other)

    def >[LL >: L](other: LL)(implicit lattice: SemiLattice[LL]): Boolean =
      lattice.<(other, element)

    def intersects[LL >: L](other: LL)(implicit lattice: SemiLattice[LL]): Boolean =
      lattice.intersects(element, other)
  }
}