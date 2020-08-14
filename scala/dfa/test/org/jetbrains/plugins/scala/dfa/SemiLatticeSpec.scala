package org.jetbrains.plugins.scala.dfa

import org.jetbrains.plugins.scala.dfa.testutils.{ForAllChecker, ForAllGenerator}
import org.scalatest.matchers.should
import org.scalatest.prop.Whenever
import org.scalatest.propspec.AnyPropSpec

trait SemiLatticeSpec[L] extends AnyPropSpec with Whenever with ForAllChecker with should.Matchers {
  protected def lattice: SemiLattice[L]
  protected def latticeHasTop: Option[HasTop[L]]
  protected def latticeHasBottom: Option[HasBottom[L]]

  protected def latticeElementSamples: Seq[L]
  protected implicit lazy val latticeElementSamplesGenerator: ForAllGenerator[L] = {
    assert(latticeHasTop.forall { implicit hasTop => latticeElementSamples.contains(latticeTop) })
    assert(latticeHasBottom.forall { implicit hasBottom => latticeElementSamples.contains(latticeBottom) })
    assert(latticeElementSamples.distinct == latticeElementSamples)
    ForAllGenerator.from(latticeElementSamples)
  }

  private implicit lazy val _lattice: SemiLattice[L] = lattice

  /*********************************** intersects ***********************************/
  latticeHasBottom.foreach { implicit latticeHasBottom =>
    property("intersects is reflexive without bottom (X != Bottom => X intersects X)") {
      forAll { (x: L) =>
        whenever(x != latticeBottom) {
          assert(x intersects x)
        }
      }
    }

    property("intersects is not reflexive in bottom") {
      (latticeBottom intersects latticeBottom) should not be true
    }
  }

  property("intersects is symmetric (X intersects Y <=> Y intersects X)") {
    forAll { (x: L, y: L) =>
      (x intersects y) shouldBe (y intersects x)
    }
  }

  /*********************************** <= ***********************************/
  property("<= is reflexive (X <= X)") {
    forAll { (x: L) =>
      assert(x <= x)
    }
  }

  property("<= is antisymmetric (X <= Y and Y <= X) <=> (X == Y)") {
    forAll { (x: L, y: L) =>
      (x <= y && y <= x) shouldBe (x == y)
    }
  }

  property("<= is transitive (X <= Y && Y <= Z => X <= Z)") {
    forAll { (x: L, y: L, z: L) =>
      whenever(x <= y && y <= z) {
        assert(x <= z)
      }
    }
  }

  /************************ additional constraints ************************/
  // this is wrong in product- or powerset-lattices, because they can have things in common while not being ordered
  //property("(X intersects Y) => (X <= Y || Y <= X)") {
  //  forAll { (x: L, y: L) =>
  //    whenever(x intersects y) {
  //      assert(x <= y || y <= x)
  //    }
  //  }
  //}

  property("(X < Y) <=> (X <= Y and X != Y)") {
    forAll { (x: L, y: L) =>
      (x < y) shouldBe (x <= y && x != y)
    }
  }

  property("(X >= Y) <=> (Y <= X)") {
    forAll { (x: L, y: L) =>
      (x >= y) shouldBe (y <= x)
    }
  }

  property("(X > Y) <=> (Y < X)") {
    forAll { (x: L, y: L) =>
      (x > y) shouldBe (y < x)
    }
  }

  property("Equal elements have equal hash") {
    forAll { (x: L, y: L) =>
      whenever(x == y) {
        x.hashCode() shouldBe y.hashCode()
      }
    }
  }
}
