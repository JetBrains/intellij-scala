package org.jetbrains.plugins.scala
package lang
package transformation
package general

/**
  * @author Pavel Fatin
  */
class ExpandForComprehensionTest extends TransformerTest(new ExpandForComprehension()) {

  def testForeach(): Unit = check(
    before = "for(v <- Seq(A)) { v.a() }",
    after = "Seq(A).foreach(v => v.a())"
  )()

  def testMap(): Unit = check(
    before = "for(v <- Seq(A)) yield v.a()",
    after = "Seq(A).map(v => v.a())"
  )()

  def testFilter(): Unit = check(
    before = "for(v <- Seq(A) if p(v)) yield v.a()",
    after = "Seq(A).withFilter(v => p(v)).map(v => v.a())"
  )()

  def testFlatMap(): Unit = check(
    before = "for(v1 <- Seq(A); v2 <- Seq(B)) yield (v1, v2)",
    after = "Seq(A).flatMap(v1 => Seq(B).map(v2 => (v1, v2)))"
  )()

  def testEmptyBlock(): Unit = check(
    before = "for(v <- Seq(A)) {}",
    after = "Seq(A).foreach(v => {})"
  )()

  def testCompoundBlock(): Unit = check(
    before = "for(v <- Seq(A)) { ???; ??? }",
    after = "Seq(A).foreach(v => { ???; ??? })"
  )()

  def testTypedPattern(): Unit = check(
    before = "for(v: A <- Seq(A)) { ??? }",
    after = "Seq(A).foreach((v: A) => ???)"
  )()

  def testTuplePattern(): Unit = check(
    before = "for((v1, v2) <- Seq(A)) { ??? }",
    after = "Seq(A).foreach { case (v1, v2) => ??? }"
  )()

  // TODO add more tests
}
