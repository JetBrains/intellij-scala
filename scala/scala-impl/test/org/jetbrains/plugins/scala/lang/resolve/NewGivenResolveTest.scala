package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class NewGivenResolveTest extends SimpleResolveTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_6


  def testUnconditionalGivens(): Unit = checkTextHasNoErrors(
    """
      |given Int = ???
      |given x: String = ???
      |
      |trait A
      |given A {
      |  def foo: Int = ???
      |}
      |
      |object Test {
      |  println(summon[Int])
      |  println(summon[String])
      |  println(summon[A].foo)
      |}
      |""".stripMargin
  )

  def testConditionalGivens(): Unit = checkTextHasNoErrors(
    """
      |trait Cond
      |
      |given Cond => Int = ???
      |given x: Cond => String = ???
      |
      |trait A
      |given Cond => A {
      |  def foo: Int = ???
      |}
      |
      |object Test {
      |  given Cond
      |
      |  println(summon[Int])
      |  println(summon[String])
      |  println(summon[A].foo)
      |}
      |""".stripMargin
  )

  def testEmptyCondition(): Unit = checkTextHasNoErrors(
    """
      |given () => Int = ???
      |
      |object Test {
      |  println(summon[Int])
      |}
      |""".stripMargin
  )
}
