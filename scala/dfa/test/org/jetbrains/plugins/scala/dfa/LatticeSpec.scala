package org.jetbrains.plugins.scala.dfa

trait LatticeSpec[L] extends JoinSemiLatticeSpec[L] with MeetSemiLatticeSpec[L] {
  override protected val lattice: Lattice[L]

  override protected lazy val latticeHasBottom: Option[HasBottom[L]] = Some(lattice)
  override protected lazy val latticeHasTop: Option[HasTop[L]] = Some(lattice)

  private implicit val _lattice: Lattice[L] = lattice

  property("absorption law") {
    forAll { (x: L, y: L) =>
      (x join (x meet y)) shouldBe x
      (x meet (x join y)) shouldBe x
    }
  }
}
