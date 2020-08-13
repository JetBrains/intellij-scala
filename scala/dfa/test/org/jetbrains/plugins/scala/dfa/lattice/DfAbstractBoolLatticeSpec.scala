package org.jetbrains.plugins.scala.dfa
package lattice

class DfAbstractBoolLatticeSpec extends LatticeSpec[DfBool] {
  override protected lazy val lattice: Lattice[DfBool] = DfBool.lattice

  override protected lazy val latticeElementSamples: Seq[DfBool] =
    DfAbstractBoolLatticeSpec.latticeElementSamples
}

object DfAbstractBoolLatticeSpec {
  val latticeElementSamples: Seq[DfBool] =
    Seq(DfBool.Top, DfBool.True, DfBool.False, DfNothing)
}