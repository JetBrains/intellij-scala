package org.jetbrains.plugins.scala.dfa

trait LatticeSpec[L] extends JoinSemiLatticeSpec[L] with MeetSemiLatticeSpec[L] {
  override protected def lattice: Lattice[L]

  private implicit lazy val _lattice: Lattice[L] = lattice

  property("absorption law") {
    forAll { (x: L, y: L) =>
      (x join (x meet y)) shouldBe x
      (x meet (x join y)) shouldBe x
    }
  }
}
