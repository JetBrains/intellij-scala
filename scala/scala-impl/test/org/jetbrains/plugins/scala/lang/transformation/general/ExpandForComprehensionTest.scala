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

  def testStringInterpolationConsistency(): Unit = check(
    before = "for(v <- Seq(A)) yield s\"${3 + v}\"",
    after = "Seq(A).map(v => s\"${3 + v}\")"
  )()

  /*
    // not working, see irrefutable comment
    def testPatternMatching(): Unit = check(
      before = "for(v: A <- Seq(A, 4)) v.a()",
      after = "Seq(A, 4).foreach { case v: A => v.a(); case _ => }"
    )()

    def testPatternMatchingWithYield(): Unit = check(
      before = "for(v: A <- Seq(A, 4)) yield v.a()",
      after = "Seq(A, 4).collect { case v: A => v.a() }"
    )()
  */

  def testPatternMatchingWithCorrectType(): Unit = check(
    before = "for((v1, v2) <- Seq((A, A))) yield v1.a()",
    after = "Seq((A, A)).map { case (v1, v2) => v1.a() }"
  )()

  def testNotDesugaringInnerForComprehensionInEnumerators(): Unit = check(
    before = "for (a <- for (b <- List(A)) b) yield a",
    after = "(for (b <- List(A)) b).map(a => a)"
  )()

  def testNotDesugaringInnerForComprehensionsInBody(): Unit = check(
    before = "for (a <- List(A)) yield for (b <- List(a)) yield b",
    after = "List(A).map(a => for (b <- List(a)) yield b)"
  )()

  def testMultipleEnumerators(): Unit = check(
    before = "for (a <- List(A); (b, c) = a; d = c) yield d",
    after = "List(A).map(a => (a, a)).map { case (a, (b, c)) => (a, (b, c), c) }.map { case (a, (b, c), d) => d }",
  )()

  def testMultipleGuards(): Unit = check(
    before = "for (a <- List(A); b = a if p(b); c = b) c.v()",
    after = "List(A).map(a => (a, a)).withFilter { case (a, b) => p(b) }.map { case (a, b) => (a, b, b) }.foreach { case (a, b, c) => c.v() }"
  )()

  def testForWithUnderscoreGenerator(): Unit = check(
    before = "for (a <- _) yield a",
    after = "(forAnonParam$0) => forAnonParam$0.map(a => a)"
  )()

  def testWithoutFilterWith(): Unit = check(
    before = "for (v <- new W if p(v)) v.p()",
    after = "(new W).filter(v => p(v)).foreach(v => v.p())"
  )(header = "class W { def filter(f: (A) => Boolean): W\n def foreach(f: (A) => Unit }")
}
