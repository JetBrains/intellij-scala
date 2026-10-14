package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3ParameterUntuplingTest extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testSimple(): Unit = doTest(
    s"""
       |object A {
       |  val xs: List[(Int, Int)] = ???
       |  ${START}xs.map { (x, y) => x + y }$END
       |}
       |//List[Int]
       |""".stripMargin
  )

  def testUnderscores(): Unit = doTest(
    s"""
       |object A {
       |  val xs: List[(Int, Int)] = ???
       |  ${START}xs.map(_ + _)$END
       |}
       |//List[Int]
       |""".stripMargin
  )

  def testSubClassParams(): Unit = doTest(
    s"""
       |object A {
       |  trait Baz
       |  trait Qux
       |  trait R
       |  trait Foo extends Baz
       |  trait Bar extends Qux
       |  val xs: List[(Foo, Bar)] = ???
       |  def combine(x: Baz, y: Qux): R = ???
       |  ${START}xs.map(combine)$END
       |}
       |//List[A.R]
       |""".stripMargin
  )

  def testMethodReference(): Unit = doTest(
    s"""
       |object A {
       |  val xs: List[(Int, Int)] = ???
       |  def combine(x: Int, y: Int): Int = x + y
       |  ${START}xs.map(combine)$END
       |}
       |//List[Int]
       |""".stripMargin
  )

  def testNonLiteralNeg(): Unit = checkHasErrorAroundCaret(
    s"""
       |object A {
       |  val xs: List[(Int, Int)] = ???
       |  val combiner: (Int, Int) => Int = ???
       |  xs.map(co${CARET}mbiner)
       |}
       |""".stripMargin
  )

  def testUntuplingNoAnnotations(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  val xs: List[(Int, Int)] = ???
      |  xs.map { (x, y) => x + y }
      |}
      |""".stripMargin
  )

  def testUntuplingCorrectAnnotations(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  val xs: List[(Int, Int)] = ???
      |  xs.map { (x: Int, y: Int) => x + y }
      |}
      |""".stripMargin
  )

  def testUntuplingWrongAnnotations(): Unit = checkHasErrorAroundCaret(
    s"""
       |object A {
       |  val xs: List[(Int, Int)] = ???
       |  xs.map { (x: S${CARET}tring, y: Int) => x + y }
       |}
       |""".stripMargin
  )

  def testSCL24556(): Unit = doTest(
    s"""
       |object A {
       |  def foo(f: ((Int, Int)) => Unit): Unit = f((1, 2))
       |  def foo(f: Unit): Unit = ???
       |  foo { (a, b) => ${START}a$END + b }
       |}
       |//Int
       |""".stripMargin
  )

  def testSCL24556Operator(): Unit = doTest(
    s"""
       |object A {
       |  trait Bar
       |  implicit object Bar extends Bar
       |
       |  trait Observer[-A] {
       |    def onNext(a: A): Unit
       |    def onError(err: Throwable): Unit
       |  }
       |
       |  class EventProp[Ev](v: Ev) {
       |    def -->(f: Ev => Unit): Unit = f(v)
       |    def -->(f: Observer[Ev]): Unit = f.onNext(v)
       |    def -->(f: Unit)(implicit ev: Bar): Unit = f
       |  }
       |
       |  new EventProp((3, 4)) --> { (a, b) => a + ${START}b$END }
       |}
       |//Int
       |""".stripMargin
  )

  def testSCL24556PatternMatchingFunction(): Unit = doTest(
    s"""
       |object A {
       |  def foo(f: ((Int, Int)) => Unit): Unit = f((1, 2))
       |  def foo(f: Unit): Unit = ???
       |  foo { case (a, b) =>
       |    ${START}a + b$END
       |    ()
       |  }
       |}
       |//Int
       |""".stripMargin
  )

  // a function literal isn't untupled, if one of the alternatives expects a function with as many parameters
  def testSCL24556SameArityAlternative(): Unit = doTest(
    s"""
       |object A {
       |  def foo(f: (Int, Int) => Int): Int = ???
       |  def foo(f: ((String, String)) => Int): String = ???
       |  ${START}foo((a, b) => a + b)$END
       |}
       |//Int
       |""".stripMargin
  )

  // `(a, b) => a + b` isn't untupled, since the first alternative expects a function with two parameters for it,
  // even though the alternative isn't applicable to `(x, y) => x * y`, so neither of the alternatives is applicable
  def testSCL24556SameArityAlternativeNotApplicable(): Unit = checkHasErrorAroundCaret(
    s"""
       |object A {
       |  def foo(f: (Int, Int) => Int, g: Int => Int): Int = ???
       |  def foo(f: ((Int, Int)) => Int, g: (Int, Int) => Int): Int = ???
       |  ${CARET}foo((a, b) => a + b, (x, y) => x * y)
       |}
       |""".stripMargin
  )

  // only a function type with as many parameters prevents untupling, a SAM type doesn't
  def testSCL24556SamAlternative(): Unit = doTest(
    s"""
       |object A {
       |  def foo(f: ((Int, Int)) => Int): String = ???
       |  def foo(f: java.util.function.BiFunction[Int, Int, Int]): Int = ???
       |  ${START}foo((a, b) => a + b)$END
       |}
       |//String
       |""".stripMargin
  )

  def testSCL24556NoErrors(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  def foo1(f: Function1[(Int, Int), Unit]): Unit = {}
      |  def foo1(f: Unit): Unit = {}
      |  foo1 { t => t._1 + t._2 }
      |  foo1 { (a, b) => a + b }
      |  foo1 { case (a, b) => a + b }
      |
      |  def foo2(f: Function2[Int, Int, Unit]): Unit = {}
      |  def foo2(f: Unit): Unit = {}
      |  foo2 { (a, b) => a + b }
      |}
      |""".stripMargin
  )
}
