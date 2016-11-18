package org.jetbrains.plugins.scala.lang.transformation.general

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class ExpandForComprehensionTest extends TransformerTest(new ExpandForComprehension()) {
  def testForeach() = check(
    "for(v <- Seq(A)) { v.a() }",
    "Seq(A).foreach(v => v.a())"
  )

  def testMap() = check(
    "for(v <- Seq(A)) yield v.a()",
    "Seq(A).map(v => v.a())"
  )

  def testFilter() = check(
    "for(v <- Seq(A) if p(v)) yield v.a()",
    "Seq(A).withFilter(v => p(v)).map(v => v.a())"
  )

  def testFlatMap() = check(
    "for(v1 <- Seq(A); v2 <- Seq(B)) yield (v1, v2)",
    "Seq(A).flatMap(v1 => Seq(B).map(v2 => (v1, v2)))"
  )

  def testEmptyBlock() = check(
    "for(v <- Seq(A)) {}",
    "Seq(A).foreach(v => {})"
  )

  def testCompoundBlock() = check(
    "for(v <- Seq(A)) { ???; ??? }",
    "Seq(A).foreach(v => { ???; ??? })"
  )

  def testTypedPattern() = check(
    "for(v: A <- Seq(A)) { ??? }",
    "Seq(A).foreach((v: A) => ???)"
  )

  def testTuplePattern() = check(
    "for((v1, v2) <- Seq(A)) { ??? }",
    "Seq(A).foreach { case (v1, v2) => ??? }"
  )

  // TODO add more tests
}
