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
    expected = (is[ScEnumCase], "Baz")
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
    expected = (is[ScEnumCase], "Some")
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
    expected = (is[ScEnumCase], "None")
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
}
