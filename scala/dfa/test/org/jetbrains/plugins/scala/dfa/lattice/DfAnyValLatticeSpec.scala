package org.jetbrains.plugins.scala.dfa
package lattice

class DfAnyValLatticeSpec extends LatticeSpec[DfAnyVal] {
  override protected lazy val lattice: Lattice[DfAnyVal] = DfAnyVal.lattice

  override protected lazy val latticeElementSamples: Seq[DfAnyVal] = (
    Seq(
      DfAnyVal.Top,
      DfAnyVal.Bottom
    ) ++ (
      for {
        a <- DfUnitLatticeSpec.latticeElementSamples
        b <- DfAbstractBoolLatticeSpec.latticeElementSamples
        c <- DfNumericLatticeSpec.latticeElementSamples
      } yield join[DfAnyVal](a, b, c)
    )
  ).distinct
}
