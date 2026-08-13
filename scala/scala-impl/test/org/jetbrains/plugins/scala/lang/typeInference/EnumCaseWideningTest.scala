package org.jetbrains.plugins.scala.lang.typeInference
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class EnumCaseWideningTest extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testWidenApplyResult(): Unit = doTest(
    s"""
       |enum Foo {
       |  case Bar(x: Int)
       |}
       |
       |object Test {
       |  val bar = ${START}Foo.Bar(1)$END
       |}
       |//Foo
       |""".stripMargin
  )

  def testWidenApplyWithTypeParameters(): Unit = doTest(
    s"""
       |enum Either[+A, +B] {
       |  case Left(l: A)
       |  case Right(r: B)
       |}
       |
       |object Test {
       |  import Either._
       |  val r = ${START}Right(123)$END
       |}
       |//Either[Nothing, Int]
       |""".stripMargin
  )

  //@TODO: testCopyMethod
  //       investigate why dotc does not widen apply() type in
  //       enum Foo { case Bar(x: Int) }; Bar(123).copy(x = 456)

  def testWidenWithExpectedType(): Unit = doTest(
    s"""
      |enum Option[+T] {
      |  case Some(x: T)
      |  case None
      |}
      |
      |object Test {
      |  import Option._
      |  val some: Some[Int] = ${START}Some(123)$END
      |}
      |//Option.Some[Int]
      |""".stripMargin
  )

  def testDoesNotConformToExpected(): Unit = doTest(
    s"""
       |enum Foo[+T] {
       |  case Bar(x: T)
       |}
       |
       |object Test {
       |  import Foo._
       |  val x: Foo[Int] = ${START}Bar("123")$END
       |}
       |//Foo.Bar[String]
       |""".stripMargin
  )

  def testNew(): Unit = doTest(
    s"""
       |enum Option[+T] {
       |  case Some(x: T)
       |  case None
       |}
       |
       |object Test {
       |  import Option._
       |  val x = ${START}new Some(123)$END
       |}
       |//Option.Some[Int]
       |""".stripMargin
  )

  def testExplicitExtendsBlock(): Unit = doTest(
    s"""
       |trait X
       |trait Y
       |enum Foo {
       |  case Bar(x: Int) extends Foo with X with Y
       |}
       |
       |object A {
       |  val x = ${START}Foo.Bar(1)$END
       |}
       |//Foo & X & Y
       |""".stripMargin
  )

  def testFunctionType(): Unit = doTest(
    s"""
      |enum Option[T] {
      |  case Some(x: T)(y: T) extends Option[T]
      |  case None             extends Option[Nothing]
      |}
      |
      |object A {
      |  import Option._
      |  val a = ${START}Option.Some(12223)$END
      |}
      |//Int => Option[Int]
      |""".stripMargin
  )

  def testSCL21386(): Unit = checkTextHasNoErrors(
    """
      |enum Color {
      |  case Green
      |}
      |
      |object A {
      |  val p: Product = Color.Green
      |  val s: Serializable = Color.Green
      |}
      |""".stripMargin
  )

  def testSCL21393(): Unit = checkHasErrorAroundCaret(
    s"""
      |object A {
      |  enum Color { case Green }
      |  object Bar
      |  val b: Bar.type = Co${CARET}lor.Green
      |}
      |""".stripMargin
  )

  def test_widening_in_generic_call_enum(): Unit = doTest(
    s"""
       |enum Color { case Green }
       |object A {
       |  val x = Color.Green
       |  val y = ${START}x$END
       |}
       |//Color
       |""".stripMargin
  )

  // The widening above happens on the `val`, the enum case itself is a singleton, just like in the
  // compiler, where `Color.Green` is a `(Color.Green : Color)` and only `val x = Color.Green` is a `Color`
  def test_no_widening_of_the_enum_case_itself(): Unit = doTest(
    s"""
       |enum Color { case Green }
       |object A {
       |  val x = ${START}Color.Green$END
       |}
       |//Color.Green.type
       |""".stripMargin
  )

  def test_widening_in_generic_call_literal_type(): Unit = doTest(
    s"""
       |object A {
       |  val x: 1 = 1
       |  val y = x
       |  val z = ${START}y$END
       |}
       |//Int
       |""".stripMargin
  )

  def test_SCL23271(): Unit = checkTextHasNoErrors(
    s"""
       |object Main {
       |  enum E {
       |    case A, B, C
       |  }
       |
       |  object E {
       |    val all = Set(A) + B + C
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    println(E.all)
       |  }
       |}
       |
       |""".stripMargin
  )
}
