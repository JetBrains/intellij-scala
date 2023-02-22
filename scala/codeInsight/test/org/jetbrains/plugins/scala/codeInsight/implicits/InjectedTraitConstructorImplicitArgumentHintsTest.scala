package org.jetbrains.plugins.scala.codeInsight.implicits
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class InjectedTraitConstructorImplicitArgumentHintsTest extends ImplicitHintsTestBase {
  import Hint.{End => E, Start => S}

  override protected def supportedIn(version: ScalaVersion) =
    version >= LatestScalaVersions.Scala_3_0

  def testSimpleImplicit(): Unit = doTest(
    s"""
       |trait Foo(implicit x: Int)
       |trait Bar extends Foo
       |
       |object A extends Bar$S with Foo(?: Int)$E
     """.stripMargin
  )

  def testMultipleIndirectParents(): Unit = doTest(
    s"""
       |trait Foo(implicit x: Int)
       |trait Bar(using y: String)
       |trait Baz extends Bar with Foo
       |
       |class A extends Baz$S with Bar(?: String) with Foo(?: Int)$E
       |
       |""".stripMargin
  )

  def testImplementedBySuperClass(): Unit = doTest(
    s"""
       |trait Foo(implicit x: Int)
       |trait Bar(implicit s: String)
       |trait Baz extends Foo with Bar
       |class Super extends Foo()$S.explicitly$E(123)
       |
       |class Test extends Super with Baz$S with Bar(?: String)$E
       |""".stripMargin
  )

  def testImplementedDirectlyNoHint(): Unit = doTest(
    s"""
       |trait X
       |trait Foo(implicit x: X)
       |
       |class Bar extends Foo$S(?: X)$E
       |""".stripMargin

  )


  def testPropagateFromConstructor(): Unit = doTest(
    s"""
       |trait Foo(implicit x: Int)
       |trait Bar(implicit s: String)
       |trait Baz extends Foo with Bar
       |
       |class A(implicit val x1: Int, implicit val x2: String) extends Baz$S with Foo(x1) with Bar(x2)$E
       |
       |""".stripMargin
  )

  def testNewTemplate(): Unit = doTest(
    s"""
       |
       |trait Foo(implicit x: Int)
       |trait Bar(implicit s: String)
       |trait Baz extends Foo with Bar
       |
       |new Baz$S with Foo(?: Int) with Bar(?: String)$E{}
       |""".stripMargin
  )

  def testGiven(): Unit = doTest(
    s"""
       |trait Foo(using x: String)
       |trait Bar extends Foo
       |
       |given Bar$S with Foo(?: String)$E with {
       |}
       |""".stripMargin
  )

  def testSubstitutorIsApplied(): Unit = doTest(
    s"""
       |trait Foo[A](implicit a: A)
       |trait Bar extends Foo[String]
       |
       |enum X extends Bar$S with Foo(?: String)$E{}
       |""".stripMargin
  )
}
