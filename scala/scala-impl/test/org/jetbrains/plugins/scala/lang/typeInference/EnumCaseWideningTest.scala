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
       |//Foo with Product
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
       |//Either[Nothing, Int] with Product
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
       |//Foo with X with Y with Product
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
      |//Int => Option[Int] with Product
      |""".stripMargin
  )
}
