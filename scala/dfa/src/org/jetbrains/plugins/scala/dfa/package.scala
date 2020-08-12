package org.jetbrains.plugins.scala

package object dfa {
  trait HasTop[+L] {
    def top: L
  }

  def latticeTop[L](implicit provider: HasTop[L]): L = provider.top


  trait HasBottom[+L] {
    def bottom: L
  }

  def latticeBottom[L](implicit provider: HasBottom[L]): L = provider.bottom


  /**
   * Type class to make [[L]] a semi-lattice
   *
   * A [[SemiLattice]] is an algebraic structure with a set of elements that are partially ordered.
   *
   * @see [[JoinSemiLattice]] and [[MeetSemiLattice]] for more operations.
   *
   * @tparam L the type to implement the semi-lattice for
   */
  trait SemiLattice[-L] {
    def <=(subSet: L, superSet: L): Boolean
    def <(subSet: L, superSet: L): Boolean = this.<=(subSet, superSet) && subSet != superSet
    def intersects(lhs: L, rhs: L): Boolean
  }

  implicit final class SemiLatticeOps[L](private val element: L) extends AnyVal {
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

    def joinAll(first: L, others: TraversableOnce[L]): L =
      others.foldLeft(first)(join)
  }

  implicit final class JoinSemiLatticeOps[L](private val element: L) extends AnyVal {
    def join[LL >: L](other: LL)(implicit lattice: JoinSemiLattice[LL]): LL =
      lattice.join(element, other)

    def | [LL >: L](other: LL)(implicit lattice: JoinSemiLattice[LL]): LL =
      lattice.join(element, other)

    def joinAll[LL >: L](others: TraversableOnce[LL])(implicit lattice: JoinSemiLattice[LL]): LL =
      lattice.joinAll(element, others)
  }

  def join[L: JoinSemiLattice](first: L, others: L*): L =
    first.joinAll(others)

  def join[L: JoinSemiLattice: HasBottom](elements: TraversableOnce[L]): L = {
    val it = elements.toIterator
    if (it.hasNext) it.next().joinAll(it)
    else latticeBottom
  }



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

    def meetAll(first: L, others: TraversableOnce[L]): L =
      others.foldLeft(first)(meet)
  }

  implicit final class MeetSemiLatticeOps[L](private val element: L) extends AnyVal {
    def meet[LL >: L](other: LL)(implicit lattice: MeetSemiLattice[LL]): LL =
      lattice.meet(element, other)

    def & [LL >: L](other: LL)(implicit lattice: MeetSemiLattice[LL]): LL =
      lattice.meet(element, other)

    def meetAll[LL >: L](others: TraversableOnce[LL])(implicit lattice: MeetSemiLattice[LL]): LL =
      lattice.meetAll(element, others)
  }

  def meet[L: MeetSemiLattice](first: L, others: L*): L =
    first.meetAll(others)

  def meet[L: MeetSemiLattice: HasTop](elements: TraversableOnce[L]): L = {
    val it = elements.toIterator
    if (it.hasNext) it.next().meetAll(it)
    else latticeTop
  }


  /**
   * A complete lattice, which is both a join-semi-lattice and meet-semi-lattice.
   *
   *        X
   *      /  \
   *     /    \
   *    A      B
   *     \    /
   *      \  /
   *       Y
   *
   * @tparam L the type to implement the lattice for
   */
  type Lattice[L] = JoinSemiLattice[L] with MeetSemiLattice[L]
}
