package org.jetbrains.plugins.scala.dfa

import org.jetbrains.plugins.scala.dfa.testutils.{ForAllChecker, ForAllGenerator}
import org.scalatest.matchers.should
import org.scalatest.prop.Whenever
import org.scalatest.propspec.AnyPropSpec

trait SemiLatticeSpec[L] extends AnyPropSpec with Whenever with ForAllChecker with should.Matchers {
  protected val lattice: SemiLattice[L]
  protected val latticeElementSamples: Seq[L]
  protected implicit lazy val latticeElementSamplesGenerator: ForAllGenerator[L] =
    ForAllGenerator.from(latticeElementSamples)

  private implicit val _lattice: SemiLattice[L] = lattice

  /*
    // Not true! Bottom does not intersect with Bottom
    property("intersects is reflexive (X intersects X)") {
      forAll { (x: L) =>
        assert(x intersects x)
      }
    }
  */

  property("intersects is symmetric (X intersects Y <=> Y intersects X)") {
    forAll { (x: L, y: L) =>
      (x intersects y) shouldBe (y intersects x)
    }
  }

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

  property("(X intersects Y) => (X <= Y || Y <= X)") {
    forAll { (x: L, y: L) =>
      whenever(x intersects y) {
        assert(x <= y || y <= x)
      }
    }
  }

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
}
