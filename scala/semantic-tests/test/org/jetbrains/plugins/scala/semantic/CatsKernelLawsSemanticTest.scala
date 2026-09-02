package org.jetbrains.plugins.scala.semantic

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.semantic.SemanticTestBase.given
import org.junit.Test

class CatsKernelLawsSemanticTest extends SemanticTestBase("org.typelevel" %% "cats-kernel-laws" % "2.13.0")("cats.kernel.laws", "cats.platform") {
  override protected def enableKindProjectorPlugin = true

  @Test def test(): Unit = doTest("""
    //cats.kernel.laws.BandLaws
    cats.kernel.laws.BoundedEnumerableLaws
    cats.kernel.laws.BoundedSemilatticeLaws
    cats.kernel.laws.CommutativeGroupLaws
    cats.kernel.laws.CommutativeMonoidLaws
    //cats.kernel.laws.CommutativeSemigroupLaws
    //cats.kernel.laws.EqLaws
    //cats.kernel.laws.GroupLaws
    //cats.kernel.laws.HashLaws
    cats.kernel.laws.IsEq
    //cats.kernel.laws.LowerBoundedLaws
    //cats.kernel.laws.MonoidLaws
    //cats.kernel.laws.OrderLaws
    //cats.kernel.laws.PartialNextBoundedLaws
    //cats.kernel.laws.PartialNextLaws
    //cats.kernel.laws.PartialOrderLaws
    //cats.kernel.laws.PartialPreviousBoundedLaws
    //cats.kernel.laws.PartialPreviousLaws
    //cats.kernel.laws.PartialPreviousNextLaws
    //cats.kernel.laws.SemigroupLaws
    cats.kernel.laws.SemilatticeLaws
    //cats.kernel.laws.SerializableLaws
    //cats.kernel.laws.UpperBoundedLaws
    //cats.kernel.laws.discipline.BandTests
    //cats.kernel.laws.discipline.BoundedEnumerableTests
    //cats.kernel.laws.discipline.BoundedSemilatticeTests
    //cats.kernel.laws.discipline.CommutativeGroupTests
    //cats.kernel.laws.discipline.CommutativeMonoidTests
    //cats.kernel.laws.discipline.CommutativeSemigroupTests
    //cats.kernel.laws.discipline.EqTests
    //cats.kernel.laws.discipline.GroupTests
    //cats.kernel.laws.discipline.HashTests
    //cats.kernel.laws.discipline.LowerBoundedTests
    //cats.kernel.laws.discipline.MonoidTests
    //cats.kernel.laws.discipline.OrderTests
    //cats.kernel.laws.discipline.PartialNextTests
    //cats.kernel.laws.discipline.PartialOrderTests
    //cats.kernel.laws.discipline.PartialPreviousTests
    //cats.kernel.laws.discipline.SemigroupTests
    //cats.kernel.laws.discipline.SemilatticeTests
    //cats.kernel.laws.discipline.SerializableTests
    //cats.kernel.laws.discipline.UpperBoundedTests
    //cats.platform.Platform
  """)
}