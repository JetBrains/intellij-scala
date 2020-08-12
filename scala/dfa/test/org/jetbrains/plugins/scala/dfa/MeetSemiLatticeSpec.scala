package org.jetbrains.plugins.scala.dfa

import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor3, TableFor4}

trait MeetSemiLatticeSpec[L] extends SemiLatticeSpec[L] with TableDrivenPropertyChecks {
  override protected val lattice: MeetSemiLattice[L]
  protected val latticeHasTop: Option[HasTop[L]]
  protected val latticeMeetSamples: TableFor3[L, L, L]

  private implicit val _lattice: MeetSemiLattice[L] = lattice

  property("Meet of two elements should be correct") {
    forAll(latticeMeetSamples) { (arg1, arg2, result) =>
      arg1 meet arg2 shouldBe result
    }
  }

  lazy val latticeMultiMeetSamples: TableFor4[L, L, L, L] =
    Table(
      ("A", "B", "C", "A meet B meet C"),
      (
        for {
          a <- latticeElementSamples
          b <- latticeElementSamples
          c <- latticeElementSamples
        } yield (a, b, c, a meet b meet c)
      ):_*
    )

  property("meet(...) should work for multiple arguments") {
    forAll(latticeMultiMeetSamples) { (a, b, c, result) =>
      meet(a, b, c) shouldBe result
      latticeHasTop.foreach { implicit hasTop =>
        meet(Seq(a, b, c)) shouldBe result
      }
    }
  }

  // only when lattice has a Top element
  latticeHasTop.foreach { implicit hasTop =>
    property("meet(Seq.empty) should be Top") {
      meet(Seq.empty[L]) shouldBe latticeTop
    }

    property("meet with Top is identity") {
      forAll { (x: L) =>
        (x meet latticeTop) shouldBe x
      }
    }
  }


  property("All elements are >= Bottom") {
    forAll { (element: L) =>
      assert(latticeBottom <= element)
    }
  }

  property("meet is reflexive (X meet X == X)") {
    forAll { (x: L) =>
      (x meet x) shouldBe x
    }
  }

  property("meet is commutative (X meet Y == Y meet X)") {
    forAll { (x: L, y: L) =>
      (x meet y) shouldBe (y meet x)
    }
  }

  property("meet is associative") {
    forAll { (x: L, y: L, z: L) =>
      ((x meet y) meet z) shouldBe (x meet (y meet z))
    }
  }

  property("(X meet Y != Bottom) <=> (X intersects Y)") {
    forAll { (x: L, y: L) =>
      withClue(s"[x meet y = ${x meet y}][x intersects y = ${x intersects y}]") {
        ((x meet y) != latticeBottom) shouldBe (x intersects y)
      }
    }
  }

  property("(X meet Y == X) <=> (X <= Y)") {
    forAll { (x: L, y: L) =>
      withClue(s"[x meet y = ${x meet y}]") {
        ((x meet y) == x) shouldBe (x <= y)
      }
    }
  }
}
