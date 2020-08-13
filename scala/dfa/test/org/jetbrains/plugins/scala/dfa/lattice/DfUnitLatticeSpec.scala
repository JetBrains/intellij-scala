package org.jetbrains.plugins.scala.dfa
package lattice

class DfUnitLatticeSpec extends LatticeSpec[DfUnit] {
  override protected def lattice: Lattice[DfUnit] = DfUnit.lattice

  override protected def latticeElementSamples: Seq[DfUnit] =
    Seq(DfUnit.Top, DfUnit.Bottom)
}
