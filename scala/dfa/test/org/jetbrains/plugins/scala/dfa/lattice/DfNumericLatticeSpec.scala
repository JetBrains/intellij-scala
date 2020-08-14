package org.jetbrains.plugins.scala.dfa
package lattice

class DfNumericLatticeSpec extends LatticeSpec[DfInt.Abstract] {
  override protected def lattice: Lattice[DfInt.Abstract] = DfInt.lattice

  override protected def latticeElementSamples: Seq[DfInt.Abstract] =
    DfNumericLatticeSpec.latticeElementSamples
}

object DfNumericLatticeSpec {
  val latticeElementSamples: Seq[DfInt.Abstract] = Seq(
    DfInt.Top,
    DfInt.Bottom,
    DfInt(-5),
    DfInt(0),
    DfInt(Int.MaxValue)
  )
}