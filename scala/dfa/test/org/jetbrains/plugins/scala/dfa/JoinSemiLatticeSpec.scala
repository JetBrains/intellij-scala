package org.jetbrains.plugins.scala.dfa

import org.scalatest.prop._

trait JoinSemiLatticeSpec[L] extends SemiLatticeSpec[L] with TableDrivenPropertyChecks {
  override protected val lattice: JoinSemiLattice[L]
  protected val latticeHasBottom: Option[HasBottom[L]]
  protected val latticeJoinSamples: TableFor3[L, L, L]

  private implicit val _lattice: JoinSemiLattice[L] = lattice

  property("Join of two elements should be correct") {
    forAll(latticeJoinSamples) { (arg1, arg2, result) =>
      arg1 join arg2 shouldBe result
    }
  }

  lazy val latticeMultiJoinSamples: TableFor4[L, L, L, L] =
    Table(
      ("A", "B", "C", "A join B join C"),
      (
        for {
          a <- latticeElementSamples
          b <- latticeElementSamples
          c <- latticeElementSamples
        } yield (a, b, c, a join b join c)
      ):_*
    )

  property("join(...) should work for multiple arguments") {
    forAll(latticeMultiJoinSamples) { (a, b, c, result) =>
      join(a, b, c) shouldBe result
      latticeHasBottom.foreach { implicit hasTop =>
        join(Seq(a, b, c)) shouldBe result
      }
    }
  }

  // only when lattice has a Bottom element
  latticeHasBottom.foreach { implicit hasTop =>
    property("join(Seq.empty) should be Bottom") {
      join(Seq.empty[L]) shouldBe latticeBottom
    }

    property("join with Bottom is identity") {
      forAll { (x: L) =>
        (x join latticeBottom) shouldBe x
      }
    }
  }


  property("All elements are <= Top") {
    forAll { (element: L) =>
      assert(element <= latticeTop)
    }
  }

  property("join is reflexive (X join X == X)") {
    forAll { (x: L) =>
      (x join x) shouldBe x
    }
  }

  property("join is commutative (X join Y == Y join X)") {
    forAll { (x: L, y: L) =>
      (x join y) shouldBe (y join x)
    }
  }

  property("join is associative") {
    forAll { (x: L, y: L, z: L) =>
      ((x join y) join z) shouldBe (x join (y join z))
    }
  }

  property("(X <= X join Y)") {
    forAll { (x: L, y: L) =>
      assert(x <= (x join y))
    }
  }

  property("(X join Y == Y) <=> (X <= Y)") {
    forAll { (x: L, y: L) =>
      withClue(s"[x join y = ${x join y}]") {
        ((x join y) == y) shouldBe (x <= y)
      }
    }
  }
}
