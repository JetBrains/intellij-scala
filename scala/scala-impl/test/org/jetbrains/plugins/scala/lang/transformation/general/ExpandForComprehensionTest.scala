package org.jetbrains.plugins.scala.lang.transformation.general

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.transformation.TransformerTest
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

abstract class ExpandForComprehensionTestBase extends TransformerTest(new ExpandForComprehension())

class ExpandForComprehensionTest extends ExpandForComprehensionTestBase {

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
    after = "Seq(A).withFilter { case (v1, v2) => true; case _ => false }.foreach { case (v1, v2) => ??? }"
  )()

  def testStringInterpolationConsistency(): Unit = check(
    before = "for(v <- Seq(A)) yield s\"${3 + v}\"",
    after = "Seq(A).map(v => s\"${3 + v}\")"
  )()

  def testPatternMatching(): Unit = check(
    before = "for((a: A, b) <- Seq((A, B), 4)) a.a()",
    after = "Seq((A, B), 4).withFilter { case (a: A, b) => true; case _ => false }.foreach { case (a: A, b) => a.a() }"
  )()

  def testPatternMatchingWithYield(): Unit = check(
    before = "for((a: A, b) <- Seq((A, B), 4)) yield a.a()",
    after = "Seq((A, B), 4).withFilter { case (a: A, b) => true; case _ => false }.map { case (a: A, b) => a.a() }"
  )()

  def testPatternMatchingWithCorrectType(): Unit = check(
    before = "for((v1, v2) <- Seq((A, B))) yield v1.a()",
    after = "Seq((A, B)).map { case (v1, v2) => v1.a() }"
  )()

  def testNotDesugaringInnerForComprehensionInEnumerators(): Unit = check(
    before = "for (a <- for (b <- List(A)) yield b) yield a",
    after = "(for (b <- List(A)) yield b).map(a => a)"
  )()

  def testNotDesugaringInnerForComprehensionsInBody(): Unit = check(
    before = "for (a <- List(A)) yield for (b <- List(a)) yield b",
    after = "List(A).map(a => for (b <- List(a)) yield b)"
  )()

  def testMultipleForBindings(): Unit = check(
    before = "for (a <- List(A); (b, c) = a; d = c) yield d",
    after = "List(A).map { a => val v$1@(b, c) = a; (a, v$1) }.map { case (a, (b, c)) => val d = c; (a, (b, c), d) }.map { case (a, (b, c), d) => d }"
  )()

  def testMultipleGuards(): Unit = check(
    before = "for (a <- List(A); b = a if p(b); if p(a); c = b) c.v()",
    after = "List(A).map { a => val b = a; (a, b) }.withFilter { case (a, b) => p(b) }.withFilter { case (a, b) => p(a) }.map { case (a, b) => val c = b; (a, b, c) }.foreach { case (a, b, c) => c.v() }"
  )()

  def testForWithMultipleUnderscores(): Unit = check(
    before = "for (_ <- _; _ = _ if _) yield _",
    after = "(forAnonParam$0, forAnonParam$1, forAnonParam$2, forAnonParam$3) => forAnonParam$0.map { _ => val _ = forAnonParam$1; () }.withFilter(_ => forAnonParam$2).map(_ => forAnonParam$3)"
  )()

  def testSimpleWildcardPattern(): Unit = check(
    before = "for (_ <- Seq(A)) yield 1",
    after = "Seq(A).map(_ => 1)"
  )()

  def testWildcardPatternWithForBindings(): Unit = check(
    before = "for (_ <- Seq(A); a = 1; _ = 2; if true; _ = 3) yield 4",
    after = "Seq(A).map { _ => val a = 1; a }.map { a => val _ = 2; a }.withFilter(a => true).map { a => val _ = 3; a }.map(a => 4)"
  )()

  def testForWithUnrelatedUnderscores(): Unit = check(
    before = "for (a <- Seq(A)) yield a + _",
    after = "Seq(A).map(a => a + _)"
  )()

  def testWithoutFilterWith(): Unit = check(
    before = "for (v <- new W if p(v)) v.p()",
    after = "(new W).filter(v => p(v)).foreach(v => v.p())"
  )(header = "class W { def filter(f: (A) => Boolean): W = ???\n def foreach(f: (A) => Unit) = ??? }")

  def test_SCL14584(): Unit = check(
    before = "for { x <- Option(1) } yield { val y = 2; val z = 3; x + y + z }",
    after = "Option(1).map(x => { val y = 2; val z = 3; x + y + z })"
  )()

  def test_SCL14779(): Unit = check(
    before = "for { a <- Some(1); b <- Some(1) } println(a)",
    after = "Some(1).foreach(a => Some(1).foreach(b => println(a)))"
  )()

  // this looks stupid but only the linebreaks are important!
  // indention gets fixed by reformat
  val space = " "
  def test_with_linebreaks(): Unit = {
    check(
      before =
        """
          |for (x <- Seq(1, 2, 3); xx = x if x == xx; xxx = xx; y <- Seq("a", "b"); yy = y if y == yy; yyy = yy) yield {
          |  println(x + xx + xxx)
          |  println(y + yy + yyy)
          |}
          |""".stripMargin,
      after =
        s"""
           |Seq(1, 2, 3)
           |  .map { x => val xx = x; (x, xx) }
           |  .withFilter { case (x, xx) => x == xx }
           |  .map { case (x, xx) => val xxx = xx; (x, xx, xxx) }
           |  .flatMap { case (x, xx, xxx) =>
           |    Seq("a", "b")
           |      .map { y => val yy = y; (y, yy) }
           |      .withFilter { case (y, yy) => y == yy }
           |      .map { case (y, yy) => val yyy = yy; (y, yy, yyy) }
           |      .map { case (y, yy, yyy) => {
           |        println(x + xx + xxx)
           |        println(y + yy + yyy)
           |      }
           |      }
           |  }
           |""".stripMargin
    )()
  }

  def test_with_linebreaks_in_enumerator(): Unit = {
    check(
      before =
        """
          |for {
          |  x <- {
          |    println("1")
          |    Seq(1)
          |  }
          |  y = {
          |    println("2")
          |    2
          |  }
          |  if {
          |    println("3")
          |    true
          |  }
          |  z = {
          |    println("4")
          |    4
          |  }
          |  zz <- {
          |    println("5")
          |    Seq(5)
          |  }
          |} println("6")
        """.stripMargin,
      after =
        s"""
           |{
           |  println("1")
           |  Seq(1)
           |}
           |  .map { x =>
           |    val y = {
           |      println("2")
           |      2
           |    }
           |
           |    (x, y)
           |  }
           |  .withFilter { case (x, y) => {
           |    println("3")
           |    true
           |  }
           |  }
           |  .map { case (x, y) =>
           |    val z = {
           |      println("4")
           |      4
           |    }
           |
           |    (x, y, z)
           |  }
           |  .foreach { case (x, y, z) => {
           |    println("5")
           |    Seq(5)
           |  }
           |    .foreach(zz => println("6"))
           |  }
           |"""
          .stripMargin
    )()
  }

  //List((174,299))
  // see #SCL-16463
  def testMutationInForBinding(): Unit = check(
    before =
      """
        |val xs = Array(1, 0, 0)
        |for {
        |  i <- 1 until xs.length
        |  previous = xs(i - 1)
        |} {
        |  xs(i) = previous
        |}
        |""".stripMargin,
    after=
      s"""
         |val xs = Array(1, 0, 0)
         |(1 until xs.length)
         |  .map { i => val previous = xs(i - 1); (i, previous) }
         |  .foreach { case (i, previous) => xs(i) = previous }
         |""".stripMargin
  )()
}

class ExpandForComprehensionTest_WithBetterMonadicFor extends ExpandForComprehensionTestBase {
  override protected def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      plugins = defaultProfile.getSettings.plugins :+ "better-monadic-for"
    )
    defaultProfile.setSettings(newSettings)
  }


  def testTuplePattern(): Unit = check(
    before = "for((v1, v2) <- Seq(A)) { ??? }",
    after = "Seq(A).foreach { case (v1, v2) => ??? }"
  )()

  def testPatternMatching(): Unit = check(
    before = "for((a: A, b) <- Seq((A, B), 4)) a.a()",
    after = "Seq((A, B), 4).foreach { case (a: A, b) => a.a() }"
  )()

  def testPatternMatchingWithYield(): Unit = check(
    before = "for((a: A, b) <- Seq((A, B), 4)) yield a.a()",
    after = "Seq((A, B), 4).map { case (a: A, b) => a.a() }"
  )()
}

class ExpandForComprehensionTest_Scala3 extends ExpandForComprehensionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  private def check3(
    @Language("Scala3") before: String,
    @Language("Scala3") after: String
  ): Unit = {
    super.check(before, after)("", "")
  }

  //SCL-19811
  def testPatternMatchInForGuard(): Unit = {
    check3(
      before =
        """for {
          |  n <- 1 to 8 if n match {
          |    case x if x > 5 => true
          |    case _ => false
          |  }
          |} yield n
          |""".stripMargin,
      after =
        """(1 to 8)
          |  .withFilter(n =>
          |    n match {
          |      case x if x > 5 => true
          |      case _ => false
          |    }
          |  )
          |  .map(n =>
          |    n
          |  )
          |""".stripMargin
    )
  }

  // SCL-22383
  def test_desugar_with_fewer_braces(): Unit = check3(
    before =
      """
        |val foo = for
        |  x <- List(1, 3, 5).map { i =>
        |    i + 1
        |  }
        |  y <- List(2, 4, 6).map: i =>
        |    i * 2
        |yield y - x
        |""".stripMargin,
    after =
      """
        |val foo = {
        |  List(1, 3, 5).map { i =>
        |    i + 1
        |  }
        |}
        |  .flatMap(x => {
        |    List(2, 4, 6).map: i =>
        |      i * 2
        |  }
        |    .map(y =>
        |      y - x
        |    )
        |  )
        |""".stripMargin,
  )
}