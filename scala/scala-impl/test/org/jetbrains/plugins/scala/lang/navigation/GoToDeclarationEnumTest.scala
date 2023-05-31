package org.jetbrains.plugins.scala.lang.navigation

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScEnum
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class GoToDeclarationEnumTest extends GotoDeclarationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testGotoSimpleCase(): Unit = doTest(
    s"""
       |enum Foo {
       |  case Bar, Baz, Qux
       |}
       |
       |object Test {
       |  val bar = Foo.Ba${CARET}z
       |}
       |""".stripMargin,
    expected = (is[ScEnumCase], "Foo.Baz")
  )

  def testClassCase(): Unit = doTest(
    s"""
       |enum Option[+T] {
       |  case Some(x: T)
       |  case None
       |}
       |
       |object Test {
       |  val someInt = Option.S${CARET}ome(123)
       |}
       |""".stripMargin,
    expected = (is[ScEnumCase], "Option.Some")
  )

  def testClassCase_NestedScope(): Unit = doTest(
    s"""object Wrapper1:
       |  class Wrapper2:
       |     enum Option[+T]:
       |       case Some(x: T)
       |       case None
       |
       |     object Test:
       |       val someInt = Option.S${CARET}ome(123)
       |""".stripMargin,
    expected = (is[ScEnumCase], "Wrapper1.Wrapper2.Option.Some")
  )

  def testWithExplicitCompanion(): Unit = doTest(
    s"""
       |enum Option[+T] {
       |  case Some(x: T)
       |  case None
       |}
       |
       |object Option {}
       |
       |object Test {
       |  val none = Option.Non${CARET}e
       |}
       |""".stripMargin,
    expected = (is[ScEnumCase], "Option.None")
  )

  def testTypePosition(): Unit = doTest(
    s"""
       |enum Option[+T] {
       |  case Some(x: T) extends Optio${CARET}n[T]
       |  case None       extends Option[Nothing]
       |}
       |""".stripMargin,
    expected = (is[ScEnum], "Option")
  )

  def testImportSimpleCase(): Unit = doTest(
    s"""
       |object Model {
       |  enum Foo {
       |    case Bar, Baz, Qux
       |  }
       |}
       |
       |def test(): Unit = {
       |  import Model.Foo.Ba${CARET}z
       |}
       |""".stripMargin,
    expected = (is[ScEnumCase], "Model.Foo.Baz")
  )

  def testImportCaseWithConstructor(): Unit = doTest(
    s"""
       |object Model {
       |  enum Foo {
       |    case Bar
       |    case Baz(i: Int)
       |  }
       |}
       |
       |def test(): Unit = {
       |  import Model.Foo.Ba${CARET}z
       |}
       |""".stripMargin,
    expected = (is[ScEnumCase], "Model.Foo.Baz")
  )

  def testSCL21150(): Unit = doTest(
    s"""
       |enum Move(val score: Int) {
       |  case Rock extends Move(1)
       |  case Paper extends Mo${CARET}ve(2)
       |  case Scissors extends Move(3)
       |}
       |""".stripMargin
  )
}
