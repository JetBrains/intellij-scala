package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class DeferredGivenAnnotatorTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_6

  def testSimpleDeferred(): Unit = checkHasErrorAroundCaret(
    s"""
       |trait Ord[A]
       |trait Foo { given ord: Ord[Int] = scala.compiletime.deferred }
       |${START}class Bar extends Foo$END
       |""".stripMargin
  )

  def testSimpleDeferredNeg(): Unit = checkTextHasNoErrors(
    s"""
       |trait Ord[A]
       |trait Foo { given ord: Ord[Int] = scala.compiletime.deferred }
       |class Bar(using Ord[Int]) extends Foo
       |""".stripMargin
  )

  def testSyntheticDeferred(): Unit = checkHasErrorAroundCaret(
    s"""
       |trait Show[A]
       |trait Foo { type Self : Show as show }
       |${START}class Bar extends Foo$END
       |""".stripMargin
  )

  def testSyntheticDeferredNeg(): Unit = checkTextHasNoErrors(
    s"""
       |trait Show[A]
       |trait Foo { type Self : Show as show }
       |
       |given Show[String] = ???
       |class Bar extends Foo {
       |  type Self = String
       |}
       |""".stripMargin
  )

  def testHasSuperClass(): Unit = checkTextHasNoErrors(
    s"""
       |trait Ord[A]
       |trait Foo { type Self : Ord as ord }
       |class Bar(using Ord[Int]) extends Foo {
       |  type Self = Int
       |}
       |class Baz extends Bar()(???)
       |""".stripMargin
  )
}
