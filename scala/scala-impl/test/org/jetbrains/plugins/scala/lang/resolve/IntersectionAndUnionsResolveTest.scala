package org.jetbrains.plugins.scala.lang.resolve
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class IntersectionAndUnionsResolveTest extends SimpleResolveTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testUnionPos(): Unit = checkTextHasNoErrors(
    s"""
       |object A {
       |  trait Base { def b: Int = 123 }
       |  trait Foo extends Base
       |  trait Bar extends Base
       |  val union: Foo | Bar = ???
       |  val x = union.b
       |}
       |""".stripMargin
  )

  def testUnionNeg(): Unit = checkHasErrorAroundCaret(
    s"""
       |object A {
       |  trait Base { def b: Int = 123 }
       |  trait Foo extends Base { def foo: Int = 123 }
       |  trait Bar extends Base
       |  val union: Foo | Bar = ???
       |  val foo = union.fo${CARET}o
       |}
       |""".stripMargin
  )

  def testIntersectionSimple(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  trait Resettable:
      |    def reset(): Unit
      |
      |  trait Growable[A]:
      |    def add(a: A): Unit
      |
      |  def f(x: Resettable & Growable[String]): Unit =
      |    x.reset()
      |    x.add("first")
      |}
      |""".stripMargin
  )

  def testSignatureIntercsection(): Unit = checkTextHasNoErrors(
    s"""
       |object A {
       |  trait TwitterPost
       |  trait InstagramPost
       |
       |  trait Singer:
       |    def share: TwitterPost
       |
       |  trait Dancer:
       |    def share: InstagramPost
       |
       |  def share(person: Singer & Dancer): TwitterPost & InstagramPost =
       |    person.share
       |}
       |""".stripMargin
  )

  //@TODO: fix when bounds are merged
  def testSignatureIntersectionSameDesignator(): Unit = checkTextHasNoErrors(
    s"""
       |object A {
       |  trait Foo { def xs: List[Int] = ??? }
       |  trait Bar { def xs: List[String] = ??? }
       |  def x(xx: Foo & Bar): List[Int with String] = xx.xs
       |}
       |""".stripMargin
  )

  def testSignatureIntersectionSubst(): Unit = checkTextHasNoErrors(
    s"""
       |object A {
       |  trait Foo[A] { def o: Option[A] = ??? }
       |  trait Bar[B] { def o: Option[B] = ??? }
       |  def x(xx: Foo[Int] & Bar[String]): Option[Int with String] = xx.o
       |}
       |""".stripMargin
  )
}
