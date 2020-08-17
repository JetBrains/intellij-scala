package org.jetbrains.plugins.scala.dfa
package lattice

class DfAnyRefLatticeSpec extends LatticeSpec[DfAnyRef] {
  override protected lazy val lattice: Lattice[DfAnyRef] = DfAnyRef.lattice

  override protected lazy val latticeElementSamples: Seq[DfAnyRef] = Seq(
    DfAnyRef.Top,
    DfAnyRef.Bottom,
    new DfStringRef("blub"),
    new DfStringRef("test")
  )
}
