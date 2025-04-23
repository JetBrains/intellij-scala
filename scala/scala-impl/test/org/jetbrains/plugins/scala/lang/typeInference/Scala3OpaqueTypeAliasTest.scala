package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class Scala3OpaqueTypeAliasTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3_0

  def testLhsLhsInside(): Unit = {
    checkTextHasNoErrors(
      """
        |object Inside:
        |  opaque type T = Int
        |  val v: T = ??? : T
      """.stripMargin
    )
  }

  def testLhsRhsInside(): Unit = {
    checkTextHasNoErrors(
      """
        |object Inside:
        |  opaque type T = Int
        |  val v: T = ??? : Int
      """.stripMargin
    )
  }

  def testRhsLhsInside(): Unit = {
    checkTextHasNoErrors(
      """
        |object Inside:
        |  opaque type T = Int
        |  val v: Int = ??? : T
      """.stripMargin
    )
  }

  def testLhsLhsOutside(): Unit = {
    checkTextHasNoErrors(
      """
        |object Inside:
        |  opaque type T = Int
        |object Outside:
        |  val v: Inside.T = ??? : Inside.T
      """.stripMargin
    )
  }

  def testLhsRhsOutside(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |object Outside:
         |  val v: Inside.T = ??? : ${CARET}Int
      """.stripMargin
    )
  }

  def testRhsLhsOutside(): Unit = {
    checkHasErrorAroundCaret(
      s"""
        |object Inside:
        |  opaque type T = Int
        |object Outside:
        |  val v: Int = ??? : ${CARET}Inside.T
      """.stripMargin
    )
  }

  def testNestedScope(): Unit = {
    checkTextHasNoErrors(
      s"""
         |class Inside:
         |  opaque type T = Int
         |  class Nested:
         |    val v: Int = ??? : T
         |""".stripMargin
    )
  }

  def testCompanionObject(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Inside:
         |  opaque type T = Int
         |object Inside:
         |  val v: Int = ??? : ${CARET}Inside#T
         |""".stripMargin
    )
  }

  def testCompanionClass(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Inside:
         |  val v: Int = ??? : ${CARET}Inside.T
         |object Inside:
         |  opaque type T = Int
         |""".stripMargin
    )
  }

  def testTransitive1(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  val v: Int = ??? : Outside.T
         |object Outside:
         |  type T = Inside.T
      """.stripMargin
    )
  }

  def testTransitive2(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |object Outside:
         |  type T = Inside.T
         |  val v: Int = ??? : ${CARET}T
      """.stripMargin
    )
  }

  def testComponent1(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  val v: Option[Int] = ??? : Option[T]
      """.stripMargin
    )
  }

  def testComponent2(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |object Outside:
         |  val v: Option[Int] = ??? : ${CARET}Option[Inside.T]
      """.stripMargin
    )
  }

  def testComposite1(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type TC[A] = Option[A]
         |  val v: Option[Int] = ??? : TC[Int]
      """.stripMargin
    )
  }

  def testComposite2(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type TC[A] = Option[A]
         |object Outside:
         |  val v: Option[Int] = ??? : ${CARET}Inside.TC[Int]
      """.stripMargin
    )
  }

  def testCompositeAndComponent1(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  opaque type TC[A] = Option[A]
         |  val v: Option[Int] = ??? : TC[T]
      """.stripMargin
    )
  }

  def testCompositeAndComponent2(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  opaque type TC[A] = Option[A]
         |object Outside:
         |  val v: Option[Int] = ??? : ${CARET}Inside.TC[Inside.T]
      """.stripMargin
    )
  }

  def testSuperType1(): Unit = {
    checkTextHasNoErrors(
      s"""
         |class TC[A]
         |object Inside:
         |  opaque type T = Int
         |  class Foo extends TC[T]
         |  val v: TC[Int] = ??? : Foo
      """.stripMargin
    )
  }

  def testSuperType2(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class TC[A]
         |object Inside:
         |  opaque type T = Int
         |  class Foo extends TC[T]
         |object Outside:
         |  val v: TC[Int] = ??? : ${CARET}Inside.Foo
      """.stripMargin
    )
  }

  def testExpression(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |object Outside:
         |  val v: Inside.T = ${CARET}123
      """.stripMargin
    )
  }

  def testPattern(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |object Outside:
         |  val Some(x) = ??? : Some[Inside.T]
         |  val y: Int = ${CARET}x
      """.stripMargin
    )
  }

  // TODO Union type, SCL-23806
  def testLeastUpperBoundIf(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |object Outside:
         |  val x = if (???) 123 else ??? : Inside.T
         |  val y: Inside.T = ${CARET}x
         |""".stripMargin
    )
  }

  def testImplicitArgumentLhsLhsInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  def find(implicit x: T): Unit = ()
         |  implicit val y: T = ???
         |  find
         |""".stripMargin
    )
  }

  def testImplicitArgumentLhsRhsInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  def find(implicit x: T): Unit = ()
         |  implicit val y: Int = ???
         |  find
         |""".stripMargin
    )
  }

  def testImplicitArgumentRhsLhsInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  def find(implicit x: Int): Unit = ()
         |  implicit val y: T = ???
         |  find
         |""".stripMargin
    )
  }

  def testImplicitArgumentLhsLhsOutside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  def find(implicit x: T): Unit = ()
         |object Outside:
         |  implicit val y: Inside.T = ???
         |  Inside.find
         |""".stripMargin
    )
  }

  def testImplicitArgumentLhsRhsOutside(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  def find(implicit x: T): Unit = ()
         |object Outside:
         |  implicit val y: Int = ???
         |  ${CARET}Inside.find
         |""".stripMargin
    )
  }

  def testImplicitArgumentRhsLhsOutside(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  def find(implicit x: Int): Unit = ()
         |object Outside:
         |  implicit val y: Inside.T = ???
         |  ${CARET}Inside.find
         |""".stripMargin
    )
  }

  def testImplicitConversionLhsLhsInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  implicit def from(x: T): Boolean = x > 0
         |  val b: Boolean = ??? : T
      """.stripMargin
    )
  }

  def testImplicitConversionLhsRhsInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  implicit def from(x: T): Boolean = x > 0
         |  val b: Boolean = ??? : Int
      """.stripMargin
    )
  }

  def testImplicitConversionRhsLhsInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  implicit def from(x: Int): Boolean = x > 0
         |  val b: Boolean = ??? : T
      """.stripMargin
    )
  }

  def testImplicitConversionLhsLhsOutside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  implicit def from(x: T): Boolean = x > 0
         |object Outside:
         |  import Inside.*
         |  val b: Boolean = ??? : T
      """.stripMargin
    )
  }

  def testImplicitConversionLhsRhsOutside(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  implicit def from(x: T): Boolean = x > 0
         |object Outside:
         |  import Inside.*
         |  val b: Boolean = ??? : ${CARET}Int
      """.stripMargin
    )
  }

  def testImplicitConversionRhsLhsOutside(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  implicit def from(x: Int): Boolean = x > 0
         |object Outside:
         |  import Inside.*
         |  val b: Boolean = ??? : ${CARET}Inside.T
         |""".stripMargin
    )
  }

  def testExtensionLhsLhsInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  extension (that: T)
         |    def method(): Unit = ???
         |  (??? : T).method()
      """.stripMargin
    )
  }

  def testExtensionLhsRhsInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  extension (that: T)
         |    def method(): Unit = ???
         |  (??? : Int).method()
      """.stripMargin
    )
  }

  def testExtensionRhsLhsInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  extension (that: Int)
         |    def method(): Unit = ???
         |  (??? : T).method()
      """.stripMargin
    )
  }

  def testExtensionLhsLhsOutside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  extension (that: T)
         |    def method(): Unit = ???
         |object Outside:
         |  import Inside.*
         |  (??? : T).method()
      """.stripMargin
    )
  }

  def testExtensionLhsRhsOutside(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  extension (that: T)
         |    def method(): Unit = ???
         |object Outside:
         |  import Inside.*
         |  (??? : Int).${CARET}method()
      """.stripMargin
    )
  }

  def testExtensionRhsLhsOutside(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  extension (that: Int)
         |    def method(): Unit = ()
         |object Outside:
         |  import Inside.*
         |  (??? : T).${CARET}method()
      """.stripMargin
    )
  }

  def testImplicitInCompanionObjectRhsLhsInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |class Foo
         |object Foo:
         |  implicit val x: Foo = ???
         |object Inside:
         |  opaque type T = Foo
         |  implicitly[T]
         |""".stripMargin
    )
  }

  def testImplicitInCompanionObjectLhsLhsInside(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  object T:
         |    implicit val x: T = ???
         |  ${CARET}implicitly[T]
         |""".stripMargin
    )
  }

  def testImplicitInCompanionObjectLhsOutside(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo
         |object Foo:
         |  implicit val x: Foo = ???
         |object Inside:
         |  opaque type T = Foo
         |object Outside:
         |  ${CARET}implicitly[Inside.T]
         |""".stripMargin
    )
  }

  def testImplicitInCompanionObjectLhsLhsOutside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T = Int
         |  object T:
         |    implicit val x: T = ???
         |object Outside:
         |  implicitly[Inside.T]
         |""".stripMargin
    )
  }

  def testContextFunctionInside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T[A] = Boolean
         |  def method(x: T[Int] ?=> Unit): Unit = ???
         |  method { implicitly[Inside.T[Int]] }
         |""".stripMargin
    )
  }

  def testContextFunctionOutside(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Inside:
         |  opaque type T[A] = Boolean
         |  def method(x: T[Int] ?=> Unit): Unit = ???
         |object Outside:
         |  Inside.method { implicitly[Inside.T[Int]] }
         |""".stripMargin
    )
  }

  def testCachingEquivalence1(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo
         |object Inside:
         |  opaque type T = Foo
         |  val x: Foo = ??? : Inside.T
         |object Outside:
         |  val y: Foo = ??? : ${CARET}Inside.T
         |""".stripMargin
    )
  }

  def testCachingEquivalence2(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo
         |object Outside:
         |  val y: Foo = ??? : ${CARET}Inside.T
         |object Inside:
         |  opaque type T = Foo
         |  val x: Foo = ??? : Inside.T
         |""".stripMargin
    )
  }

  def testCachingConformance1(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo; class Bar extends Foo
         |object Inside:
         |  opaque type T = Bar
         |  val x: Foo = ??? : Inside.T
         |object Outside:
         |  val y: Foo = ??? : ${CARET}Inside.T
         |""".stripMargin
    )
  }

  def testCachingConformance2(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |class Foo; class Bar extends Foo
         |object Outside:
         |  val y: Foo = ??? : ${CARET}Inside.T
         |object Inside:
         |  opaque type T = Bar
         |  val x: Foo = ??? : Inside.T
         |""".stripMargin
    )
  }

  def testCachingImplicits(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Inside:
         |  opaque type T = Int
         |object Outside:
         |  val x: Int = implicitly[Inside.T]
         |  val y: Int = ??? : ${CARET}Inside.T
         |""".stripMargin
    )
  }
}
