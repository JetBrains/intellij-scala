package org.jetbrains.plugins.scala.dfa

package object lattice {
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
  type Lattice[L] = lattice.JoinSemiLattice[L] with lattice.MeetSemiLattice[L]
}
