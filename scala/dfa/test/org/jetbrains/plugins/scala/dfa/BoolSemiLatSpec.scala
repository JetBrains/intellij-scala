package org.jetbrains.plugins.scala.dfa

import org.jetbrains.plugins.scala.dfa.BoolSemiLat.{False, Top, True}
import org.scalatest.prop.TableFor3

class BoolSemiLatSpec extends JoinSemiLatticeSpec[BoolSemiLat] {
  import BoolSemiLat._
  override protected lazy val lattice: JoinSemiLattice[BoolSemiLat] = BoolSemiLat.joinSemiLattice

  override protected lazy val latticeHasBottom: Option[HasBottom[BoolSemiLat]] = None

  override protected lazy val latticeElementSamples: Seq[BoolSemiLat] = BoolSemiLatSpec.latticeElementSamples

  override protected lazy val latticeJoinSamples: TableFor3[BoolSemiLat, BoolSemiLat, BoolSemiLat] =
    Table(
      ("A", "B", "A join B"),
      BoolSemiLatSpec.latticeJoinSamples:_*
    )


  val boolLat: BoolLat = Top
  val boolSemiLat: BoolSemiLat = Top

  property("BoolSemiLat join BoolSemiLat should give BoolLat") {
    val r: BoolSemiLat = boolSemiLat join boolSemiLat

    r shouldBe Top
  }

  property("BoolLat join BoolSemiLat should gives BoolLat") {
    val r1: BoolLat = boolSemiLat join boolLat
    val r2: BoolLat = boolLat join boolSemiLat

    r1 shouldBe Top
    r2 shouldBe Top
  }

  property("BoolSemiLat meet BoolLat should give BoolLat") {
    val r1: BoolLat = boolSemiLat meet boolLat
    val r2: BoolLat = boolLat meet boolSemiLat

    r1 shouldBe Top
    r2 shouldBe Top
  }
}

object BoolSemiLatSpec {
  val latticeElementSamples: Seq[BoolSemiLat] = Seq(Top, True, False)

  val latticeJoinSamples: Seq[(BoolSemiLat, BoolSemiLat, BoolSemiLat)] =
    Seq(
      (True,  True,  True),
      (False, False, False),
      (True,  False, Top),
      (False, True,  Top),
    ) ++ (
      // pairs with top
      for {
        a <- latticeElementSamples
        b <- latticeElementSamples
        if (a == Top || b == Top)
      } yield (a, b, Top)
    )
}