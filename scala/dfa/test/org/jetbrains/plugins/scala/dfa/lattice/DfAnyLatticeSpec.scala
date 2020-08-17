package org.jetbrains.plugins.scala.dfa
package lattice

class DfAnyLatticeSpec extends LatticeSpec[DfAny] {
  override protected lazy val lattice: Lattice[DfAny] = DfAny.lattice

  override protected lazy val latticeElementSamples: Seq[DfAny] = (
    Seq(
      DfAny.Top,
      DfAny.Bottom
    ) ++ DfAnyValLatticeSpec.latticeElementSamples ++ DfAnyRefLatticeSpec.latticeElementSamples
  ).distinct
}

